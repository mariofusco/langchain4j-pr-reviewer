package dev.langchain4j.prreviewer.support;

import dev.langchain4j.prreviewer.github.GitHubReads;
import dev.langchain4j.prreviewer.model.PrRef;
import io.quarkus.test.Mock;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** GitHub from the scenario's fixtures, in place of the hub4j client; excluded by the profiles that use real services. */
@Mock
public class FixtureGitHubReadsBean implements GitHubReads {

    @Inject
    TestScenario scenario;

    private GitHubReads github() {
        return scenario.github();
    }

    @Override
    public PullRequestData pullRequest(PrRef ref) {
        return github().pullRequest(ref);
    }

    @Override
    public Optional<String> unifiedDiff(PrRef ref) {
        return github().unifiedDiff(ref);
    }

    @Override
    public List<ChangedFile> files(PrRef ref) {
        return github().files(ref);
    }

    @Override
    public List<CommitInfo> commits(PrRef ref) {
        return github().commits(ref);
    }

    @Override
    public List<CheckRun> checkRuns(String owner, String repo, String sha) {
        return github().checkRuns(owner, repo, sha);
    }

    @Override
    public List<Review> reviews(PrRef ref) {
        return github().reviews(ref);
    }

    @Override
    public List<ReviewComment> reviewComments(PrRef ref) {
        return github().reviewComments(ref);
    }

    @Override
    public Issue issue(String owner, String repo, int number) {
        return github().issue(owner, repo, number);
    }

    @Override
    public Contents contents(String owner, String repo, String path, String ref) {
        return github().contents(owner, repo, path, ref);
    }

    @Override
    public List<CodeMatch> searchCode(String query) {
        return github().searchCode(query);
    }

    @Override
    public CommitDetails commit(String owner, String repo, String sha) {
        return github().commit(owner, repo, sha);
    }

    @Override
    public List<CommitInfo> listCommits(String owner, String repo, String path, String sha) {
        return github().listCommits(owner, repo, path, sha);
    }

    @Override
    public Stream<PullRequestSummary> closedPullRequests(String owner, String repo) {
        return github().closedPullRequests(owner, repo);
    }
}
