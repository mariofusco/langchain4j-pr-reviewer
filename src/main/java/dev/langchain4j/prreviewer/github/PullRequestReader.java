package dev.langchain4j.prreviewer.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.prreviewer.model.CiStatus;
import dev.langchain4j.prreviewer.model.Commit;
import dev.langchain4j.prreviewer.model.PrRef;
import dev.langchain4j.prreviewer.model.PullRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reads a pull request through the GitHub MCP tools {@code pull_request_read} and {@code issue_read}. */
public class PullRequestReader {

    private static final Logger LOG = LoggerFactory.getLogger(PullRequestReader.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern LINKED_ISSUE =
            Pattern.compile("(?i)\\b(?:fix(?:es|ed)?|close[sd]?|resolve[sd]?)\\s*:?\\s+#(\\d+)");
    private static final int MAX_COMMENTS = 30;
    private static final int MAX_COMMENT_CHARS = 500;
    private static final int FILES_PER_PAGE = 100;
    private static final int MAX_FILES_FROM_PATCHES = 300;
    private static final int MAX_PAGES = 5;

    private final GitHubGateway github;

    public PullRequestReader(GitHubGateway github) {
        this.github = github;
    }

    public PullRequest read(PrRef ref) {
        JsonNode pr = json(pullRequestRead(ref, "get", Map.of()));
        String body = text(pr, "body");

        String rawDiff = rawDiff(ref);
        List<Commit> commits = commits(ref);
        CiStatus ciStatus = optional("check runs", CiStatus.unknown(), () -> ciStatus(ref));
        List<String> comments = optional("reviews", List.of(), () -> existingReviewComments(ref));
        String linkedIssue = optional("linked issue", null, () -> linkedIssue(ref, body));

        List<String> labels = new ArrayList<>();
        pr.path("labels").forEach(label -> labels.add(label.isTextual() ? label.asText() : label.path("name").asText()));

        return new PullRequest(
                ref.owner(),
                ref.repo(),
                ref.number(),
                text(pr, "title"),
                body,
                pr.path("user").path("login").asText(""),
                pr.path("base").path("ref").asText(""),
                pr.path("head").path("ref").asText(""),
                pr.path("head").path("sha").asText(""),
                text(pr, "html_url"),
                commits,
                List.copyOf(labels),
                linkedIssue,
                ciStatus,
                comments,
                rawDiff);
    }

    /** The unified diff; when GitHub refuses to produce it (too large), it is rebuilt from the per-file patches. */
    private String rawDiff(PrRef ref) {
        try {
            String diff = unwrapDiff(pullRequestRead(ref, "get_diff", Map.of()));
            if (diff.startsWith("diff --git")) {
                return diff;
            }
            LOG.warn("Unexpected diff for {}, rebuilding it from the files", ref);
        } catch (RuntimeException e) {
            LOG.warn("Cannot read the diff of {} ({}), rebuilding it from the files", ref, e.getMessage());
        }
        return diffFromFiles(ref);
    }

    private String diffFromFiles(PrRef ref) {
        StringBuilder diff = new StringBuilder();
        int page = 1;
        int read = 0;
        while (read < MAX_FILES_FROM_PATCHES) {
            JsonNode files = array(json(pullRequestRead(ref, "get_files", Map.of("page", page, "perPage", FILES_PER_PAGE))));
            if (!files.isArray() || files.isEmpty()) {
                break;
            }
            for (JsonNode file : files) {
                diff.append(filePatch(file));
                read++;
            }
            if (files.size() < FILES_PER_PAGE) {
                break;
            }
            page++;
        }
        return diff.toString();
    }

    static String filePatch(JsonNode file) {
        String name = file.path("filename").asText();
        String previous = file.path("previous_filename").asText(name);
        String status = file.path("status").asText("modified");
        StringBuilder sb = new StringBuilder("diff --git a/").append(previous).append(" b/").append(name).append('\n');
        switch (status) {
            case "added" -> sb.append("new file mode 100644\n--- /dev/null\n+++ b/").append(name).append('\n');
            case "removed" -> sb.append("deleted file mode 100644\n--- a/").append(previous).append("\n+++ /dev/null\n");
            case "renamed" -> sb.append("rename from ").append(previous).append("\nrename to ").append(name)
                    .append("\n--- a/").append(previous).append("\n+++ b/").append(name).append('\n');
            default -> sb.append("--- a/").append(previous).append("\n+++ b/").append(name).append('\n');
        }
        String patch = file.path("patch").asText("");
        if (patch.isEmpty()) {
            if (file.path("changes").asInt(0) > 0 && !status.equals("renamed")) {
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

    private List<Commit> commits(PrRef ref) {
        List<Commit> result = new ArrayList<>();
        for (JsonNode c : paged(ref, "get_commits")) {
            result.add(new Commit(
                    c.path("sha").asText(""),
                    c.path("message").asText(c.path("commit").path("message").asText("")),
                    c.path("author").path("name").asText(c.path("author").path("login").asText(""))));
        }
        return List.copyOf(result);
    }

    /** All pages of a list-valued method, up to a sane maximum. */
    private List<JsonNode> paged(PrRef ref, String method) {
        List<JsonNode> items = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode batch = array(json(pullRequestRead(ref, method, Map.of("page", page, "perPage", FILES_PER_PAGE))));
            batch.forEach(items::add);
            if (batch.size() < FILES_PER_PAGE) {
                break;
            }
        }
        return items;
    }

    private CiStatus ciStatus(PrRef ref) {
        JsonNode checks = json(pullRequestRead(ref, "get_check_runs", Map.of()));
        int succeeded = 0;
        int failed = 0;
        int pending = 0;
        int total = 0;
        for (JsonNode run : checks.path("check_runs")) {
            total++;
            if (!"completed".equals(run.path("status").asText())) {
                pending++;
                continue;
            }
            switch (run.path("conclusion").asText("")) {
                case "success", "neutral", "skipped" -> succeeded++;
                case "failure", "timed_out", "cancelled", "action_required", "startup_failure" -> failed++;
                default -> pending++;
            }
        }
        return new CiStatus(total, succeeded, failed, pending);
    }

    private List<String> existingReviewComments(PrRef ref) {
        List<String> comments = new ArrayList<>();
        for (JsonNode review : paged(ref, "get_reviews")) {
            String body = review.path("body").asText("");
            String state = review.path("state").asText("");
            if (!body.isBlank() || "CHANGES_REQUESTED".equals(state)) {
                comments.add(review.path("user").path("login").asText("?") + " (" + state + "): " + clip(body));
            }
        }
        JsonNode threads = json(pullRequestRead(ref, "get_review_comments", Map.of())).path("review_threads");
        for (JsonNode thread : threads) {
            for (JsonNode comment : thread.path("comments")) {
                String line = comment.path("line").isNumber() ? ":" + comment.path("line").asInt() : "";
                comments.add(comment.path("author").asText("?") + " on " + comment.path("path").asText("?") + line
                        + (thread.path("is_resolved").asBoolean(false) ? " (resolved)" : "")
                        + ": " + clip(comment.path("body").asText("")));
            }
        }
        return List.copyOf(comments.size() > MAX_COMMENTS ? comments.subList(0, MAX_COMMENTS) : comments);
    }

    private String linkedIssue(PrRef ref, String body) {
        Matcher matcher = LINKED_ISSUE.matcher(body == null ? "" : body);
        if (!matcher.find()) {
            return null;
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("method", "get");
        args.put("owner", ref.owner());
        args.put("repo", ref.repo());
        args.put("issue_number", Integer.parseInt(matcher.group(1)));
        JsonNode issue = json(github.call("issue_read", args));
        if (issue.has("issue")) {
            issue = issue.get("issue");
        }
        return "#" + matcher.group(1) + " " + text(issue, "title") + "\n" + text(issue, "body");
    }

    private String pullRequestRead(PrRef ref, String method, Map<String, Object> extra) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("method", method);
        args.put("owner", ref.owner());
        args.put("repo", ref.repo());
        args.put("pullNumber", ref.number());
        args.putAll(extra);
        return github.call("pull_request_read", args);
    }

    /**
     * The GitHub MCP server returns some results as structured content, which the MCP client prefers over the text
     * content: the diff then arrives as {@code {"diff": "..."}}. Both shapes are accepted.
     */
    static String unwrapDiff(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.stripLeading();
        if (trimmed.startsWith("{")) {
            return json(trimmed).path("diff").asText("");
        }
        return trimmed;
    }

    /** A list result, also when it arrives wrapped in an object as structured content. */
    public static JsonNode array(JsonNode node) {
        if (node.isArray()) {
            return node;
        }
        for (JsonNode value : node) {
            if (value.isArray()) {
                return value;
            }
        }
        return JSON.createArrayNode();
    }

    private static <T> T optional(String what, T fallback, java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            LOG.warn("Cannot read the {} of the pull request: {}", what, e.getMessage());
            return fallback;
        }
    }

    private static JsonNode json(String text) {
        try {
            return JSON.readTree(text);
        } catch (IOException e) {
            throw new GitHubAccessException("Unexpected response from GitHub: " + clip(text), e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? "" : value.asText("");
    }

    private static String clip(String text) {
        String value = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        return value.length() <= MAX_COMMENT_CHARS ? value : value.substring(0, MAX_COMMENT_CHARS) + "…";
    }
}
