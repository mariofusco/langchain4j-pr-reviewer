package dev.langchain4j.prreviewer.github;

import dev.langchain4j.prreviewer.github.GitHubReads.ChangedFile;
import dev.langchain4j.prreviewer.github.GitHubReads.CheckRun;
import dev.langchain4j.prreviewer.github.GitHubReads.CommitInfo;
import dev.langchain4j.prreviewer.github.GitHubReads.Issue;
import dev.langchain4j.prreviewer.github.GitHubReads.PullRequestData;
import dev.langchain4j.prreviewer.github.GitHubReads.Review;
import dev.langchain4j.prreviewer.github.GitHubReads.ReviewComment;
import dev.langchain4j.prreviewer.model.CiStatus;
import dev.langchain4j.prreviewer.model.Commit;
import dev.langchain4j.prreviewer.model.PrRef;
import dev.langchain4j.prreviewer.model.PullRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Assembles a {@link PullRequest} from what {@link GitHubReads} returns. */
public class PullRequestReader {

    private static final Logger LOG = LoggerFactory.getLogger(PullRequestReader.class);
    private static final Pattern LINKED_ISSUE =
            Pattern.compile("(?i)\\b(?:fix(?:es|ed)?|close[sd]?|resolve[sd]?)\\s*:?\\s+#(\\d+)");
    private static final int MAX_COMMENTS = 30;
    private static final int MAX_COMMENT_CHARS = 500;

    private final GitHubReads github;

    public PullRequestReader(GitHubReads github) {
        this.github = github;
    }

    public PullRequest read(PrRef ref) {
        PullRequestData pr = github.pullRequest(ref);
        String rawDiff = rawDiff(ref);
        List<Commit> commits = github.commits(ref).stream().map(CommitInfo::toCommit).toList();
        CiStatus ciStatus = optional("check runs", CiStatus.unknown(), () -> ciStatus(ref, pr.headSha()));
        List<String> comments = optional("reviews", List.of(), () -> existingReviewComments(ref));
        String linkedIssue = optional("linked issue", null, () -> linkedIssue(ref, pr.body()));

        return new PullRequest(
                ref.owner(),
                ref.repo(),
                ref.number(),
                pr.title(),
                pr.body(),
                pr.author(),
                pr.baseRef(),
                pr.headRef(),
                pr.headSha(),
                pr.htmlUrl(),
                commits,
                pr.labels(),
                linkedIssue,
                ciStatus,
                comments,
                rawDiff);
    }

    /** The unified diff; when GitHub refuses to produce it (too large), it is rebuilt from the per-file patches. */
    private String rawDiff(PrRef ref) {
        try {
            Optional<String> diff = github.unifiedDiff(ref);
            if (diff.isPresent()) {
                if (diff.get().startsWith("diff --git")) {
                    return diff.get();
                }
                LOG.warn("Unexpected diff for {}, rebuilding it from the files", ref);
            } else {
                LOG.info("GitHub does not serve the diff of {} (too large), rebuilding it from the files", ref);
            }
        } catch (RuntimeException e) {
            LOG.warn("Cannot read the diff of {} ({}), rebuilding it from the files", ref, e.getMessage());
        }
        StringBuilder diff = new StringBuilder();
        for (ChangedFile file : github.files(ref)) {
            diff.append(filePatch(file));
        }
        return diff.toString();
    }

    static String filePatch(ChangedFile file) {
        String name = file.filename();
        String previous = file.previousFilename() == null || file.previousFilename().isEmpty() ? name : file.previousFilename();
        String status = file.status() == null ? "modified" : file.status();
        StringBuilder sb = new StringBuilder("diff --git a/").append(previous).append(" b/").append(name).append('\n');
        switch (status) {
            case "added" -> sb.append("new file mode 100644\n--- /dev/null\n+++ b/").append(name).append('\n');
            case "removed" -> sb.append("deleted file mode 100644\n--- a/").append(previous).append("\n+++ /dev/null\n");
            case "renamed" -> sb.append("rename from ").append(previous).append("\nrename to ").append(name)
                    .append("\n--- a/").append(previous).append("\n+++ b/").append(name).append('\n');
            default -> sb.append("--- a/").append(previous).append("\n+++ b/").append(name).append('\n');
        }
        String patch = file.patch() == null ? "" : file.patch();
        if (patch.isEmpty()) {
            if (file.changes() > 0 && !status.equals("renamed")) {
                sb.append("Binary files a/").append(previous).append(" and b/").append(name).append(" differ\n");
            }
        } else {
            sb.append(patch);
            if (!patch.endsWith("\n")) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private CiStatus ciStatus(PrRef ref, String headSha) {
        if (headSha == null || headSha.isEmpty()) {
            return CiStatus.unknown();
        }
        int succeeded = 0;
        int failed = 0;
        int pending = 0;
        int total = 0;
        for (CheckRun run : github.checkRuns(ref.owner(), ref.repo(), headSha)) {
            total++;
            if (!"completed".equals(run.status())) {
                pending++;
                continue;
            }
            switch (run.conclusion() == null ? "" : run.conclusion()) {
                case "success", "neutral", "skipped" -> succeeded++;
                case "failure", "timed_out", "cancelled", "action_required", "startup_failure" -> failed++;
                default -> pending++;
            }
        }
        return new CiStatus(total, succeeded, failed, pending);
    }

    private List<String> existingReviewComments(PrRef ref) {
        List<String> comments = new ArrayList<>();
        for (Review review : github.reviews(ref)) {
            if (!review.body().isBlank() || "CHANGES_REQUESTED".equals(review.state())) {
                comments.add(review.author() + " (" + review.state() + "): " + clip(review.body()));
            }
        }
        for (ReviewComment comment : threaded(github.reviewComments(ref))) {
            int line = comment.line() > 0 ? comment.line() : comment.originalLine();
            comments.add(comment.author() + " on " + comment.path() + (line > 0 ? ":" + line : "")
                    + ": " + clip(comment.body()));
        }
        return List.copyOf(comments.size() > MAX_COMMENTS ? comments.subList(0, MAX_COMMENTS) : comments);
    }

    /** The comments thread by thread: each root comment followed by its replies, threads in order of their roots. */
    static List<ReviewComment> threaded(List<ReviewComment> comments) {
        Map<Long, List<ReviewComment>> threads = new LinkedHashMap<>();
        for (ReviewComment comment : comments) {
            long root = comment.inReplyToId() > 0 && threads.containsKey(comment.inReplyToId())
                    ? comment.inReplyToId()
                    : comment.id();
            threads.computeIfAbsent(root, k -> new ArrayList<>()).add(comment);
        }
        return threads.values().stream().flatMap(List::stream).toList();
    }

    private String linkedIssue(PrRef ref, String body) {
        Matcher matcher = LINKED_ISSUE.matcher(body == null ? "" : body);
        if (!matcher.find()) {
            return null;
        }
        int number = Integer.parseInt(matcher.group(1));
        Issue issue = github.issue(ref.owner(), ref.repo(), number);
        return "#" + number + " " + issue.title() + "\n" + issue.body();
    }

    private static <T> T optional(String what, T fallback, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            LOG.warn("Cannot read the {} of the pull request: {}", what, e.getMessage());
            return fallback;
        }
    }

    private static String clip(String text) {
        String value = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        return value.length() <= MAX_COMMENT_CHARS ? value : value.substring(0, MAX_COMMENT_CHARS) + "…";
    }
}
