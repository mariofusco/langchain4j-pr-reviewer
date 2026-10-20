package dev.langchain4j.prreviewer.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.prreviewer.github.GitHubReads;
import dev.langchain4j.prreviewer.github.GitHubReads.ChangedFile;
import dev.langchain4j.prreviewer.github.GitHubReads.CommitInfo;
import dev.langchain4j.prreviewer.github.GitHubReads.PullRequestData;
import dev.langchain4j.prreviewer.github.GitHubReads.PullRequestSummary;
import dev.langchain4j.prreviewer.github.GitHubReads.Review;
import dev.langchain4j.prreviewer.github.GitHubReads.ReviewComment;
import dev.langchain4j.prreviewer.model.PrRef;
import dev.langchain4j.prreviewer.model.Verdict;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Builds the eval set from closed pull requests of public Java repositories, labelled from their outcomes:
 * <ul>
 *   <li>REQUEST_CHANGES when a reviewer requested changes, or the pull request was closed unmerged after a review;</li>
 *   <li>COMMENT when it was merged after review comments that the author addressed with later commits;</li>
 *   <li>APPROVE when it was merged without either;</li>
 *   <li>must-find items: review comments by someone other than the author that a later commit followed.</li>
 * </ul>
 * Outcomes reflect the maintainers' priorities as well as code quality, so the labels are a floor, not ground truth.
 */
@Command(name = "build-set", mixinStandardHelpOptions = true,
        description = "Builds the eval set from closed pull requests of public Java repositories")
public class EvalSetBuilder implements Callable<Integer> {

    static final List<String> DEFAULT_REPOS = List.of("langchain4j/langchain4j", "quarkusio/quarkus",
            "FasterXML/jackson-databind", "micrometer-metrics/micrometer", "assertj/assertj");

    /** Closed pull requests examined per repository, most recently updated first. */
    static final int MAX_CANDIDATES = 240;

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Option(names = "--repos", split = ",", description = "owner/repo list")
    List<String> repos = DEFAULT_REPOS;

    @Option(names = "--per-repo", defaultValue = "4", description = "Cases per repository")
    int perRepo;

    @Option(names = "--max-files", defaultValue = "15", description = "Skip pull requests changing more files")
    int maxFiles;

    @Option(names = "--max-lines", defaultValue = "800", description = "Skip pull requests changing more lines")
    int maxLines;

    @Option(names = "--out", defaultValue = "eval/eval-set.json")
    Path out;

    @Inject
    GitHubReads github;

    @Override
    public Integer call() throws Exception {
        List<EvalCase> cases = new ArrayList<>();
        for (String repo : repos) {
            List<EvalCase> selected = selectFrom(repo);
            System.out.printf(Locale.ROOT, "%s: %d cases%n", repo, selected.size());
            cases.addAll(selected);
        }
        Files.createDirectories(out.toAbsolutePath().getParent());
        JSON.writeValue(out.toFile(), cases);
        System.out.println(cases.size() + " cases written to " + out);
        return 0;
    }

    List<EvalCase> selectFrom(String repo) {
        String[] ownerRepo = repo.split("/");
        Map<Verdict, Integer> quota = new EnumMap<>(Map.of(Verdict.APPROVE, (perRepo + 2) / 3,
                Verdict.COMMENT, Math.max(1, perRepo / 4), Verdict.REQUEST_CHANGES, (perRepo + 2) / 3));
        List<EvalCase> selected = new ArrayList<>();
        try {
            Iterator<PullRequestSummary> candidates = github.closedPullRequests(ownerRepo[0], ownerRepo[1])
                    .limit(MAX_CANDIDATES).iterator();
            while (candidates.hasNext() && selected.size() < perRepo) {
                PullRequestSummary summary = candidates.next();
                try {
                    EvalCase candidate = label(ownerRepo[0], ownerRepo[1], summary);
                    if (candidate != null && quota.getOrDefault(candidate.expectedVerdict(), 0) > 0) {
                        quota.merge(candidate.expectedVerdict(), -1, Integer::sum);
                        selected.add(candidate);
                    }
                } catch (RuntimeException e) {
                    System.err.println("skipping " + repo + "#" + summary.number() + ": " + e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            System.err.println("stopping at " + repo + ": " + e.getMessage());
        }
        return selected;
    }

    EvalCase label(String owner, String repo, PullRequestSummary summary) {
        String login = summary.author();
        String title = summary.title();
        if (login.endsWith("[bot]") || login.contains("dependabot") || login.contains("renovate")
                || title.toLowerCase(Locale.ROOT).startsWith("bump ") || title.startsWith("chore(deps)")) {
            return null;
        }
        PrRef ref = new PrRef(owner, repo, summary.number());
        PullRequestData pr = github.pullRequest(ref);
        int changedLines = pr.additions() + pr.deletions();
        if (pr.changedFiles() == 0 || pr.changedFiles() > maxFiles || changedLines < 5 || changedLines > maxLines) {
            return null;
        }
        boolean javaChanged = false;
        for (ChangedFile f : github.files(ref)) {
            javaChanged |= f.filename().endsWith(".java");
        }
        if (!javaChanged) {
            return null;
        }
        String author = pr.author().isEmpty() ? login : pr.author();

        boolean changesRequested = false;
        boolean reviewed = false;
        for (Review review : github.reviews(ref)) {
            if (!review.author().equals(author)) {
                reviewed = true;
                changesRequested |= "CHANGES_REQUESTED".equals(review.state());
            }
        }
        Instant lastCommit = Instant.EPOCH;
        for (CommitInfo commit : github.commits(ref)) {
            if (commit.date() != null && commit.date().isAfter(lastCommit)) {
                lastCommit = commit.date();
            }
        }
        List<EvalCase.MustFind> mustFind = new ArrayList<>();
        for (ReviewComment first : github.reviewComments(ref)) {
            if (first.inReplyToId() != 0 || first.author().equals(author) || isBot(first.author())) {
                continue;
            }
            reviewed = true;
            boolean addressed = first.createdAt() != null && lastCommit.isAfter(first.createdAt());
            if (addressed || (!pr.merged() && changesRequested)) {
                int line = first.line() > 0 ? first.line() : first.originalLine();
                String body = first.body().strip().replaceAll("\\s+", " ");
                mustFind.add(new EvalCase.MustFind(first.path(), line,
                        body.length() > 300 ? body.substring(0, 300) + "…" : body));
            }
        }

        Verdict verdict;
        String rationale;
        if (changesRequested || (!pr.merged() && reviewed)) {
            verdict = Verdict.REQUEST_CHANGES;
            rationale = changesRequested ? "a reviewer requested changes" : "closed unmerged after review";
        } else if (!pr.merged()) {
            return null; // closed without review: no signal
        } else if (!mustFind.isEmpty()) {
            verdict = Verdict.COMMENT;
            rationale = "merged after " + mustFind.size() + " addressed review comments";
        } else {
            verdict = Verdict.APPROVE;
            rationale = "merged without requested changes or addressed review comments";
        }
        return new EvalCase(owner + "/" + repo + "#" + summary.number(), title, pr.merged(), pr.changedFiles(),
                changedLines, verdict, List.copyOf(mustFind), rationale);
    }

    /** Review bots comment a lot; their remarks are not what a maintainer asked for. */
    static boolean isBot(String login) {
        String l = login.toLowerCase(Locale.ROOT);
        return l.endsWith("[bot]") || l.contains("coderabbit") || l.contains("sourcery") || l.contains("copilot")
                || l.contains("sonar") || l.contains("codecov") || l.contains("github-actions") || l.contains("dependabot");
    }
}
