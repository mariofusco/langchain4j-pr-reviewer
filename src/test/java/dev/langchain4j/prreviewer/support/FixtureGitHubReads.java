package dev.langchain4j.prreviewer.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.prreviewer.github.GitHubAccessException;
import dev.langchain4j.prreviewer.github.GitHubReads;
import dev.langchain4j.prreviewer.model.PrRef;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * GitHub answered from REST responses recorded with the GitHub CLI, in {@code src/test/resources/github/<pr>}:
 * {@code pull.json}, {@code files.json}, {@code commits.json}, {@code check_runs.json}, {@code reviews.json},
 * {@code review_comments.json}, {@code issue_<n>.json} and the unified diff {@code diff.diff}. File contents are
 * synthesized, code search finds nothing. Tests override single methods to simulate what GitHub refuses.
 */
public class FixtureGitHubReads implements GitHubReads {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String pr;
    private final String diffOverride;
    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());

    /** @param pr the fixture directory, such as "pr6610" */
    public FixtureGitHubReads(String pr) {
        this(pr, null);
    }

    /** Uses the fixtures of the given pull request, but with another diff. */
    public FixtureGitHubReads(String pr, String diffOverride) {
        this.pr = pr;
        this.diffOverride = diffOverride;
    }

    /** The methods called, in order: "pullRequest", "files", "issue:6600", "contents:README.md", ... */
    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public PullRequestData pullRequest(PrRef ref) {
        calls.add("pullRequest");
        return pullRequestData(json("pull.json"));
    }

    @Override
    public Optional<String> unifiedDiff(PrRef ref) {
        calls.add("unifiedDiff");
        if (diffOverride != null) {
            return Optional.of(diffOverride);
        }
        return exists("diff.diff") ? Optional.of(read(pr + "/diff.diff")) : Optional.empty();
    }

    @Override
    public List<ChangedFile> files(PrRef ref) {
        calls.add("files");
        return changedFiles(json("files.json"));
    }

    @Override
    public List<CommitInfo> commits(PrRef ref) {
        calls.add("commits");
        return commitInfos(json("commits.json"));
    }

    @Override
    public List<CheckRun> checkRuns(String owner, String repo, String sha) {
        calls.add("checkRuns");
        List<CheckRun> runs = new ArrayList<>();
        JsonNode root = json("check_runs.json");
        for (JsonNode run : root.isArray() ? root : root.path("check_runs")) {
            runs.add(new CheckRun(run.path("name").asText(), run.path("status").asText(), nullable(run, "conclusion")));
        }
        return runs;
    }

    @Override
    public List<Review> reviews(PrRef ref) {
        calls.add("reviews");
        List<Review> reviews = new ArrayList<>();
        for (JsonNode review : json("reviews.json")) {
            reviews.add(new Review(review.path("user").path("login").asText(""), review.path("state").asText(""),
                    review.path("body").asText("")));
        }
        return reviews;
    }

    @Override
    public List<ReviewComment> reviewComments(PrRef ref) {
        calls.add("reviewComments");
        List<ReviewComment> comments = new ArrayList<>();
        for (JsonNode c : json("review_comments.json")) {
            comments.add(new ReviewComment(c.path("id").asLong(), c.path("in_reply_to_id").asLong(0),
                    c.path("user").path("login").asText(""), c.path("path").asText(""), c.path("line").asInt(0),
                    c.path("original_line").asInt(0), c.path("body").asText(""), instant(c, "created_at")));
        }
        return comments;
    }

    @Override
    public Issue issue(String owner, String repo, int number) {
        calls.add("issue:" + number);
        JsonNode issue = json("issue_" + number + ".json");
        return new Issue(number, issue.path("title").asText(""), issue.path("body").asText(""));
    }

    @Override
    public Contents contents(String owner, String repo, String path, String ref) {
        calls.add("contents:" + path);
        return new Contents(path, "Fixture content of " + path + " at " + ref + " in " + owner + "/" + repo, null);
    }

    @Override
    public List<CodeMatch> searchCode(String query) {
        calls.add("searchCode:" + query);
        return List.of();
    }

    @Override
    public CommitDetails commit(String owner, String repo, String sha) {
        calls.add("commit:" + sha);
        return commitInfos(json("commits.json")).stream()
                .filter(c -> c.sha().startsWith(sha))
                .findFirst()
                .map(c -> new CommitDetails(c, changedFiles(json("files.json"))))
                .orElseThrow(() -> new GitHubAccessException("GitHub: commit " + sha + " not found, or no access"));
    }

    @Override
    public List<CommitInfo> listCommits(String owner, String repo, String path, String sha) {
        calls.add("listCommits:" + path);
        return commitInfos(json("commits.json"));
    }

    @Override
    public Stream<PullRequestSummary> closedPullRequests(String owner, String repo) {
        calls.add("closedPullRequests");
        PullRequestData data = pullRequestData(json("pull.json"));
        return Stream.of(new PullRequestSummary(data.number(), data.title(), data.author()));
    }

    /** The raw unified diff of pull request 6610, as plain text. */
    public static String diff6610() {
        return read("pr6610/diff.diff");
    }

    public static String read(String path) {
        try (InputStream in = FixtureGitHubReads.class.getResourceAsStream("/github/" + path)) {
            if (in == null) {
                throw new IllegalArgumentException("No fixture /github/" + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static PullRequestData pullRequestData(JsonNode pr) {
        List<String> labels = new ArrayList<>();
        pr.path("labels").forEach(label -> labels.add(label.isTextual() ? label.asText() : label.path("name").asText()));
        return new PullRequestData(pr.path("number").asInt(), pr.path("title").asText(""), pr.path("body").asText(""),
                pr.path("user").path("login").asText(""), pr.path("base").path("ref").asText(""),
                pr.path("head").path("ref").asText(""), pr.path("head").path("sha").asText(""),
                pr.path("html_url").asText(""), List.copyOf(labels), pr.path("merged").asBoolean(false),
                pr.path("additions").asInt(), pr.path("deletions").asInt(), pr.path("changed_files").asInt());
    }

    private static List<ChangedFile> changedFiles(JsonNode files) {
        List<ChangedFile> result = new ArrayList<>();
        for (JsonNode file : files) {
            result.add(new ChangedFile(file.path("filename").asText(), nullable(file, "previous_filename"),
                    file.path("status").asText("modified"), nullable(file, "patch"), file.path("changes").asInt(0)));
        }
        return result;
    }

    private static List<CommitInfo> commitInfos(JsonNode commits) {
        List<CommitInfo> result = new ArrayList<>();
        for (JsonNode c : commits) {
            JsonNode commit = c.path("commit");
            result.add(new CommitInfo(c.path("sha").asText(""), commit.path("message").asText(""),
                    commit.path("author").path("name").asText(""), instant(commit.path("author"), "date")));
        }
        return result;
    }

    private boolean exists(String file) {
        return FixtureGitHubReads.class.getResource("/github/" + pr + "/" + file) != null;
    }

    private JsonNode json(String file) {
        try {
            return JSON.readTree(read(pr + "/" + file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String nullable(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asText();
    }

    private static Instant instant(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isEmpty() ? null : Instant.parse(value);
    }
}
