package dev.langchain4j.prreviewer.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.prreviewer.github.GitHubGateway;
import dev.langchain4j.prreviewer.github.GitHubMcp;
import dev.langchain4j.prreviewer.github.PullRequestReader;
import dev.langchain4j.prreviewer.model.Verdict;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
 *   <li>must-find items: review comments by someone other than the author that were addressed: their thread became
 *   outdated (a later commit changed the commented lines) or was resolved, or a later commit followed them.</li>
 * </ul>
 * Outcomes reflect the maintainers' priorities as well as code quality, so the labels are a floor, not ground truth.
 */
@Command(name = "build-set", mixinStandardHelpOptions = true,
        description = "Builds the eval set from closed pull requests of public Java repositories")
public class EvalSetBuilder implements Callable<Integer> {

    static final List<String> DEFAULT_REPOS = List.of("langchain4j/langchain4j", "quarkusio/quarkus",
            "FasterXML/jackson-databind", "micrometer-metrics/micrometer", "assertj/assertj");

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
    GitHubMcp gitHubMcp;

    @Override
    public Integer call() throws Exception {
        List<EvalCase> cases = new ArrayList<>();
        GitHubGateway github = gitHubMcp.gateway();
        for (String repo : repos) {
            List<EvalCase> selected = selectFrom(github, repo);
            System.out.printf(Locale.ROOT, "%s: %d cases%n", repo, selected.size());
            cases.addAll(selected);
        }
        Files.createDirectories(out.toAbsolutePath().getParent());
        JSON.writeValue(out.toFile(), cases);
        System.out.println(cases.size() + " cases written to " + out);
        return 0;
    }

    List<EvalCase> selectFrom(GitHubGateway github, String repo) {
        String[] ownerRepo = repo.split("/");
        Map<Verdict, Integer> quota = new EnumMap<>(Map.of(Verdict.APPROVE, (perRepo + 2) / 3,
                Verdict.COMMENT, Math.max(1, perRepo / 4), Verdict.REQUEST_CHANGES, (perRepo + 2) / 3));
        List<EvalCase> selected = new ArrayList<>();
        for (int page = 1; page <= 8 && selected.size() < perRepo; page++) {
            Map<String, Object> args = new LinkedHashMap<>(Map.of("owner", ownerRepo[0], "repo", ownerRepo[1],
                    "state", "closed", "sort", "updated", "direction", "desc", "perPage", 30, "page", page));
            JsonNode list = PullRequestReader.array(json(github.call("list_pull_requests", args)));
            for (JsonNode summary : list) {
                if (selected.size() >= perRepo) {
                    break;
                }
                try {
                    EvalCase candidate = label(github, ownerRepo[0], ownerRepo[1], summary);
                    if (candidate != null && quota.getOrDefault(candidate.expectedVerdict(), 0) > 0) {
                        quota.merge(candidate.expectedVerdict(), -1, Integer::sum);
                        selected.add(candidate);
                    }
                } catch (RuntimeException e) {
                    System.err.println("skipping " + repo + "#" + summary.path("number").asInt() + ": " + e.getMessage());
                }
            }
        }
        return selected;
    }

    EvalCase label(GitHubGateway github, String owner, String repo, JsonNode summary) {
        String login = summary.path("user").path("login").asText("");
        String title = summary.path("title").asText("");
        if (login.endsWith("[bot]") || login.contains("dependabot") || login.contains("renovate")
                || title.toLowerCase(Locale.ROOT).startsWith("bump ") || title.startsWith("chore(deps)")) {
            return null;
        }
        int number = summary.path("number").asInt();
        Map<String, Object> base = Map.of("owner", owner, "repo", repo, "pullNumber", number);
        JsonNode pr = json(call(github, "get", base));
        int changedFiles = pr.path("changed_files").asInt();
        int changedLines = pr.path("additions").asInt() + pr.path("deletions").asInt();
        if (changedFiles == 0 || changedFiles > maxFiles || changedLines < 5 || changedLines > maxLines) {
            return null;
        }
        JsonNode files = PullRequestReader.array(json(call(github, "get_files", base)));
        boolean javaChanged = false;
        for (JsonNode f : files) {
            javaChanged |= f.path("filename").asText().endsWith(".java");
        }
        if (!javaChanged) {
            return null;
        }
        boolean merged = pr.path("merged").asBoolean(false);
        String author = pr.path("user").path("login").asText(login);

        boolean changesRequested = false;
        boolean reviewed = false;
        for (JsonNode review : PullRequestReader.array(json(call(github, "get_reviews", base)))) {
            if (!review.path("user").path("login").asText().equals(author)) {
                reviewed = true;
                changesRequested |= "CHANGES_REQUESTED".equals(review.path("state").asText());
            }
        }
        Instant lastCommit = Instant.EPOCH;
        for (JsonNode commit : PullRequestReader.array(json(call(github, "get_commits", base)))) {
            String date = commit.path("author").path("date").asText("");
            if (!date.isEmpty() && Instant.parse(date).isAfter(lastCommit)) {
                lastCommit = Instant.parse(date);
            }
        }
        List<EvalCase.MustFind> mustFind = new ArrayList<>();
        for (JsonNode thread : json(call(github, "get_review_comments", base)).path("review_threads")) {
            JsonNode first = thread.path("comments").path(0);
            if (first.isMissingNode() || first.path("author").asText().equals(author)
                    || isBot(first.path("author").asText())) {
                continue;
            }
            reviewed = true;
            String created = first.path("created_at").asText("");
            boolean addressed = thread.path("is_outdated").asBoolean(false)
                    || thread.path("is_resolved").asBoolean(false)
                    || (!created.isEmpty() && lastCommit.isAfter(Instant.parse(created)));
            if (addressed || (!merged && changesRequested)) {
                int line = first.path("line").isNumber() ? first.path("line").asInt() : first.path("original_line").asInt(0);
                String body = first.path("body").asText("").strip().replaceAll("\\s+", " ");
                mustFind.add(new EvalCase.MustFind(first.path("path").asText(), line,
                        body.length() > 300 ? body.substring(0, 300) + "…" : body));
            }
        }

        Verdict verdict;
        String rationale;
        if (changesRequested || (!merged && reviewed)) {
            verdict = Verdict.REQUEST_CHANGES;
            rationale = changesRequested ? "a reviewer requested changes" : "closed unmerged after review";
        } else if (!merged) {
            return null; // closed without review: no signal
        } else if (!mustFind.isEmpty()) {
            verdict = Verdict.COMMENT;
            rationale = "merged after " + mustFind.size() + " addressed review comments";
        } else {
            verdict = Verdict.APPROVE;
            rationale = "merged without requested changes or addressed review comments";
        }
        return new EvalCase(owner + "/" + repo + "#" + number, title, merged, changedFiles, changedLines, verdict,
                List.copyOf(mustFind), rationale);
    }

    /** Review bots comment a lot; their remarks are not what a maintainer asked for. */
    static boolean isBot(String login) {
        String l = login.toLowerCase(Locale.ROOT);
        return l.endsWith("[bot]") || l.contains("coderabbit") || l.contains("sourcery") || l.contains("copilot")
                || l.contains("sonar") || l.contains("codecov") || l.contains("github-actions") || l.contains("dependabot");
    }

    private static String call(GitHubGateway github, String method, Map<String, Object> base) {
        Map<String, Object> args = new LinkedHashMap<>(base);
        args.put("method", method);
        if (method.equals("get_files") || method.equals("get_commits")) {
            args.put("perPage", 100);
        }
        return github.call("pull_request_read", args);
    }

    private static JsonNode json(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected GitHub response: " + text.substring(0, Math.min(200, text.length())));
        }
    }
}
