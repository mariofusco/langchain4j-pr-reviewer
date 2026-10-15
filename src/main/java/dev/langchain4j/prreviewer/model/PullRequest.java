package dev.langchain4j.prreviewer.model;

import java.util.List;

/** A pull request as fetched from GitHub, with its raw unified diff. */
public record PullRequest(
        String owner,
        String repo,
        int number,
        String title,
        String body,
        String author,
        String baseRef,
        String headRef,
        String headSha,
        String htmlUrl,
        List<Commit> commits,
        List<String> labels,
        String linkedIssueBody,
        CiStatus ciStatus,
        List<String> existingReviewComments,
        String rawDiff) {

    public PrRef ref() {
        return new PrRef(owner, repo, number);
    }
}
