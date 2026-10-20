package dev.langchain4j.prreviewer.github;

import dev.langchain4j.prreviewer.model.Commit;
import dev.langchain4j.prreviewer.model.PrRef;
import io.quarkus.arc.Arc;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Everything the reviewer reads from GitHub, as plain records: one implementation talks to GitHub through the hub4j
 * client, the tests answer from recorded responses. There is no write method: the reviewer is read-only by
 * construction.
 */
public interface GitHubReads {

    /** For static agent methods, which cannot have collaborators injected. */
    static GitHubReads get() {
        return Arc.container().instance(GitHubReads.class).get();
    }

    // --- the deterministic fetch of the reviewed pull request

    PullRequestData pullRequest(PrRef ref);

    /** The unified diff, or empty when GitHub refuses to produce it (too large). */
    Optional<String> unifiedDiff(PrRef ref);

    List<ChangedFile> files(PrRef ref);

    List<CommitInfo> commits(PrRef ref);

    List<CheckRun> checkRuns(String owner, String repo, String sha);

    List<Review> reviews(PrRef ref);

    /** The review comments in creation order; a reply carries the id of the comment it answers. */
    List<ReviewComment> reviewComments(PrRef ref);

    Issue issue(String owner, String repo, int number);

    // --- the tools of the AI agents

    /** A file, or the entries of a directory, at a commit, branch or tag ({@code ref} null: the default branch). */
    Contents contents(String owner, String repo, String path, String ref);

    List<CodeMatch> searchCode(String query);

    CommitDetails commit(String owner, String repo, String sha);

    /** The most recent commits, of one path when given, starting from a sha or branch when given. */
    List<CommitInfo> listCommits(String owner, String repo, String path, String sha);

    // --- the eval set

    /** The closed pull requests of a repository, most recently updated first, read lazily page by page. */
    Stream<PullRequestSummary> closedPullRequests(String owner, String repo);

    record PullRequestData(int number, String title, String body, String author, String baseRef, String headRef,
                           String headSha, String htmlUrl, List<String> labels, boolean merged, int additions,
                           int deletions, int changedFiles) {}

    record PullRequestSummary(int number, String title, String author) {}

    /** @param previousFilename the former name of a renamed file, else null */
    record ChangedFile(String filename, String previousFilename, String status, String patch, int changes) {}

    /** @param date when the commit was authored, or null when unknown */
    record CommitInfo(String sha, String message, String author, Instant date) {

        public Commit toCommit() {
            return new Commit(sha, message, author);
        }
    }

    record CommitDetails(CommitInfo commit, List<ChangedFile> files) {}

    /**
     * @param status     {@code completed}, {@code in_progress} or {@code queued}
     * @param conclusion {@code success}, {@code failure}, {@code neutral}, {@code skipped}, {@code cancelled},
     *                   {@code timed_out}, {@code action_required}, {@code stale}, or null while not completed
     */
    record CheckRun(String name, String status, String conclusion) {}

    record Review(String author, String state, String body) {}

    /**
     * @param inReplyToId  the comment this one answers, or 0 for the root comment of a thread
     * @param line         the line in the current diff, or 0 when the comment is outdated
     * @param originalLine the line in the diff the comment was made on
     */
    record ReviewComment(long id, long inReplyToId, String author, String path, int line, int originalLine,
                         String body, Instant createdAt) {}

    record Issue(int number, String title, String body) {}

    /** A file with its text, or a directory ({@code text} null) with the names of its entries. */
    record Contents(String path, String text, List<String> entries) {

        public boolean directory() {
            return text == null;
        }
    }

    record CodeMatch(String repository, String path, String htmlUrl) {}
}
