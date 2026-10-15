package dev.langchain4j.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.prreviewer.model.PrRef;
import dev.langchain4j.prreviewer.model.PullRequest;
import dev.langchain4j.prreviewer.support.FixtureGitHubGateway;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PullRequestReaderTest {

    @Test
    void reads_a_pull_request_from_recorded_mcp_responses() {
        FixtureGitHubGateway github = new FixtureGitHubGateway("pr6610");

        PullRequest pr = new PullRequestReader(github).read(new PrRef("langchain4j", "langchain4j", 6610));

        assertThat(pr.title()).isEqualTo("Decide in one place which AI Service methods call the model");
        assertThat(pr.author()).isEqualTo("dliubarskyi");
        assertThat(pr.baseRef()).isEqualTo("main");
        assertThat(pr.headSha()).isEqualTo("c4c39e6187c1443a76c428fcf1bed5c2dccb5182");
        assertThat(pr.rawDiff()).startsWith("diff --git a/langchain4j/src/main/java/dev/langchain4j/service/AiServiceValidation.java");
        assertThat(pr.commits()).isNotEmpty();
        assertThat(pr.commits().get(0).headline()).isEqualTo("Decide in one place which AI Service methods call the model");
        assertThat(pr.ciStatus().total()).isEqualTo(8);
        assertThat(pr.ciStatus().failed()).isPositive();
        assertThat(pr.linkedIssueBody()).isNull(); // "Follow-up to #6420" does not close an issue
        assertThat(github.calls()).contains("pull_request_read:get", "pull_request_read:get_diff",
                "pull_request_read:get_commits", "pull_request_read:get_check_runs", "pull_request_read:get_reviews",
                "pull_request_read:get_review_comments");
    }

    @Test
    void reads_reviews_and_review_threads() {
        GitHubGateway github = (tool, args) -> {
            String method = String.valueOf(args.get("method"));
            return switch (method) {
                case "get" -> FixtureGitHubGateway.read("pr6610/get.json");
                case "get_diff" -> FixtureGitHubGateway.diff6610();
                case "get_check_runs" -> FixtureGitHubGateway.read("pr6610/get_check_runs.json");
                default -> FixtureGitHubGateway.read("pr6584/" + method + ".json");
            };
        };

        PullRequest pr = new PullRequestReader(github).read(new PrRef("langchain4j", "langchain4j", 6584));

        assertThat(pr.existingReviewComments()).anyMatch(c -> c.startsWith("jmartisk (CHANGES_REQUESTED)"));
        assertThat(pr.existingReviewComments())
                .anyMatch(c -> c.startsWith("jmartisk on langchain4j-mcp/src/main/java/dev/langchain4j/mcp/client/DefaultMcpClient.java:814 (resolved): You probably should call"));
    }

    @Test
    void fetches_the_linked_issue_and_survives_missing_optional_parts() {
        String body = FixtureGitHubGateway.read("pr6610/get.json").replace("No issue. Follow-up to #6420.", "Fixes #6600");
        GitHubGateway github = (tool, args) -> {
            if (tool.equals("issue_read")) {
                assertThat(args).containsEntry("issue_number", 6600);
                return FixtureGitHubGateway.read("pr6610/issue_6600.json");
            }
            return switch (String.valueOf(args.get("method"))) {
                case "get" -> body;
                case "get_diff" -> FixtureGitHubGateway.diff6610();
                case "get_commits" -> "[]";
                default -> throw new GitHubAccessException("rate limited");
            };
        };

        PullRequest pr = new PullRequestReader(github).read(new PrRef("langchain4j", "langchain4j", 6610));

        assertThat(pr.linkedIssueBody()).startsWith("#6600 fix(guardrails)");
        assertThat(pr.ciStatus().total()).isZero();
        assertThat(pr.existingReviewComments()).isEmpty();
    }

    @Test
    void accepts_results_as_text_content_or_as_structured_content() {
        String plainIssue = FixtureGitHubGateway.read("plain/issue_6600.json");
        String structuredIssue = FixtureGitHubGateway.read("pr6610/issue_6600.json");
        String body = FixtureGitHubGateway.read("pr6610/get.json").replace("No issue. Follow-up to #6420.", "Closes #6600");
        for (String issue : java.util.List.of(plainIssue, structuredIssue)) {
            for (String diff : java.util.List.of(FixtureGitHubGateway.diff6610(), FixtureGitHubGateway.read("pr6610/get_diff.json"))) {
                GitHubGateway github = (tool, args) -> tool.equals("issue_read") ? issue
                        : switch (String.valueOf(args.get("method"))) {
                            case "get" -> body;
                            case "get_diff" -> diff;
                            case "get_commits" -> "{\"commits\":" + FixtureGitHubGateway.read("pr6610/get_commits.json") + "}";
                            default -> "[]";
                        };

                PullRequest pr = new PullRequestReader(github).read(new PrRef("langchain4j", "langchain4j", 6610));

                assertThat(DiffParser.parse(pr.rawDiff())).hasSize(6);
                assertThat(pr.linkedIssueBody()).startsWith("#6600 fix(guardrails)");
                assertThat(pr.commits()).isNotEmpty();
            }
        }
    }

    @Test
    void a_pure_rename_without_patch_is_not_a_binary_change() throws Exception {
        var file = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"filename\":\"src/B.java\",\"previous_filename\":\"src/A.java\",\"status\":\"renamed\",\"changes\":0}");

        String patch = PullRequestReader.filePatch(file);

        assertThat(patch).contains("rename from src/A.java").contains("rename to src/B.java").doesNotContain("Binary files");
        assertThat(DiffParser.parse(patch)).singleElement().satisfies(f -> {
            assertThat(f.kind()).isEqualTo(dev.langchain4j.prreviewer.model.ChangeKind.RENAMED);
            assertThat(f.binary()).isFalse();
        });
    }

    @Test
    void rebuilds_the_diff_from_the_files_when_github_refuses_it() {
        GitHubGateway github = (tool, args) -> switch (String.valueOf(args.get("method"))) {
            case "get" -> FixtureGitHubGateway.read("pr6610/get.json");
            case "get_diff" -> throw new GitHubAccessException("diff too large");
            case "get_files" -> Map.of("page", 1).equals(Map.of("page", args.get("page")))
                    ? FixtureGitHubGateway.read("pr6610/get_files.json")
                    : "[]";
            default -> "[]";
        };

        PullRequest pr = new PullRequestReader(github).read(new PrRef("langchain4j", "langchain4j", 6610));

        assertThat(DiffParser.parse(pr.rawDiff())).hasSize(6);
        assertThat(DiffParser.parse(pr.rawDiff()).stream().mapToInt(f -> f.additions()).sum()).isEqualTo(124);
    }
}
