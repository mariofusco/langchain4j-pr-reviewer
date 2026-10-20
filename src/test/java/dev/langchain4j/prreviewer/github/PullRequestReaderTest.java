package dev.langchain4j.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.prreviewer.github.GitHubReads.ChangedFile;
import dev.langchain4j.prreviewer.github.GitHubReads.CheckRun;
import dev.langchain4j.prreviewer.github.GitHubReads.CommitInfo;
import dev.langchain4j.prreviewer.github.GitHubReads.PullRequestData;
import dev.langchain4j.prreviewer.github.GitHubReads.Review;
import dev.langchain4j.prreviewer.github.GitHubReads.ReviewComment;
import dev.langchain4j.prreviewer.model.ChangeKind;
import dev.langchain4j.prreviewer.model.PrRef;
import dev.langchain4j.prreviewer.model.PullRequest;
import dev.langchain4j.prreviewer.support.FixtureGitHubReads;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PullRequestReaderTest {

    static final PrRef PR_6610 = new PrRef("langchain4j", "langchain4j", 6610);

    @Test
    void reads_a_pull_request_from_recorded_responses() {
        FixtureGitHubReads github = new FixtureGitHubReads("pr6610");

        PullRequest pr = new PullRequestReader(github).read(PR_6610);

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
        assertThat(github.calls()).contains("pullRequest", "unifiedDiff", "commits", "checkRuns", "reviews",
                "reviewComments");
    }

    @Test
    void reads_reviews_and_review_comments() {
        PullRequest pr = new PullRequestReader(new FixtureGitHubReads("pr6584"))
                .read(new PrRef("langchain4j", "langchain4j", 6584));

        assertThat(pr.existingReviewComments()).anyMatch(c -> c.startsWith("jmartisk (CHANGES_REQUESTED)"));
        assertThat(pr.existingReviewComments()).anyMatch(c -> c.startsWith(
                "jmartisk on langchain4j-mcp/src/main/java/dev/langchain4j/mcp/client/DefaultMcpClient.java:814: You probably should call"));
    }

    @Test
    void replies_follow_the_root_comment_of_their_thread() {
        ReviewComment first = comment(1, 0, "first");
        ReviewComment second = comment(2, 0, "second");
        ReviewComment replyToFirst = comment(3, 1, "reply to first");
        ReviewComment replyToSecond = comment(4, 2, "reply to second");
        ReviewComment orphan = comment(5, 99, "reply to a deleted comment");

        List<ReviewComment> threaded = PullRequestReader.threaded(List.of(first, second, replyToFirst, replyToSecond, orphan));

        assertThat(threaded).extracting(ReviewComment::body)
                .containsExactly("first", "reply to first", "second", "reply to second", "reply to a deleted comment");
    }

    @Test
    void fetches_the_linked_issue_and_survives_missing_optional_parts() {
        FixtureGitHubReads github = new FixtureGitHubReads("pr6610") {
            @Override
            public PullRequestData pullRequest(PrRef ref) {
                PullRequestData pr = super.pullRequest(ref);
                return new PullRequestData(pr.number(), pr.title(),
                        pr.body().replace("No issue. Follow-up to #6420.", "Fixes #6600"), pr.author(), pr.baseRef(),
                        pr.headRef(), pr.headSha(), pr.htmlUrl(), pr.labels(), pr.merged(), pr.additions(),
                        pr.deletions(), pr.changedFiles());
            }

            @Override
            public List<CommitInfo> commits(PrRef ref) {
                return List.of();
            }

            @Override
            public List<CheckRun> checkRuns(String owner, String repo, String sha) {
                throw new GitHubAccessException("rate limited");
            }

            @Override
            public List<Review> reviews(PrRef ref) {
                throw new GitHubAccessException("rate limited");
            }
        };

        PullRequest pr = new PullRequestReader(github).read(PR_6610);

        assertThat(pr.linkedIssueBody()).startsWith("#6600 fix(guardrails)");
        assertThat(pr.commits()).isEmpty();
        assertThat(pr.ciStatus().total()).isZero();
        assertThat(pr.existingReviewComments()).isEmpty();
        assertThat(github.calls()).contains("issue:6600");
    }

    @Test
    void a_pure_rename_without_patch_is_not_a_binary_change() {
        String patch = PullRequestReader.filePatch(new ChangedFile("src/B.java", "src/A.java", "renamed", null, 0));

        assertThat(patch).contains("rename from src/A.java").contains("rename to src/B.java").doesNotContain("Binary files");
        assertThat(DiffParser.parse(patch)).singleElement().satisfies(f -> {
            assertThat(f.kind()).isEqualTo(ChangeKind.RENAMED);
            assertThat(f.binary()).isFalse();
        });
    }

    @Test
    void rebuilds_the_diff_from_the_files_when_github_refuses_it() {
        FixtureGitHubReads github = new FixtureGitHubReads("pr6610") {
            @Override
            public Optional<String> unifiedDiff(PrRef ref) {
                return Optional.empty();
            }
        };

        PullRequest pr = new PullRequestReader(github).read(PR_6610);

        assertThat(DiffParser.parse(pr.rawDiff())).hasSize(6);
        assertThat(DiffParser.parse(pr.rawDiff()).stream().mapToInt(f -> f.additions()).sum()).isEqualTo(124);
        assertThat(github.calls()).contains("files");
    }

    private static ReviewComment comment(long id, long inReplyTo, String body) {
        return new ReviewComment(id, inReplyTo, "reviewer", "src/A.java", 10, 10, body, Instant.EPOCH);
    }
}
