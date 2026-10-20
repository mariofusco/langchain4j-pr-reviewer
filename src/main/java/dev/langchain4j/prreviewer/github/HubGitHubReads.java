package dev.langchain4j.prreviewer.github;

import dev.langchain4j.prreviewer.model.PrRef;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitQueryBuilder;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHException;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHPullRequestQueryBuilder;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAbuseLimitHandler;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.GitHubRateLimitHandler;
import org.kohsuke.github.GitUser;
import org.kohsuke.github.HttpException;
import org.kohsuke.github.PagedIterable;

/**
 * GitHub through the hub4j client brought by the quarkus-github-api extension, built on first use with the token of
 * {@code github.token} ({@code GITHUB_TOKEN}, else {@code gh auth token}). A rate or abuse limit fails the call
 * instead of sleeping until the limit resets, and the root error handler retries such a failure once. The unified
 * diff, which hub4j cannot produce, is read with one direct REST call. Every failure is a {@link GitHubAccessException}
 * naming what could not be read.
 */
@ApplicationScoped
@Unremovable
public class HubGitHubReads implements GitHubReads {

    static final int PAGE_SIZE = 100;
    /** Files read when a diff is rebuilt from the per-file patches. */
    static final int MAX_FILES = 300;
    static final int MAX_ITEMS = 500;
    static final int MAX_SEARCH_RESULTS = 30;
    static final int MAX_LISTED_COMMITS = 20;
    /** Longer files are cut: a model reads them, and the contents API itself stops at 1 MB. */
    static final int MAX_FILE_CHARS = 120_000;
    static final String TRUNCATED = "\n[truncated: the file is longer than " + MAX_FILE_CHARS + " characters]";
    private static final String API = "https://api.github.com";
    private static final Duration DIFF_TIMEOUT = Duration.ofSeconds(60);

    @ConfigProperty(name = "github.token")
    Optional<String> token;

    private final Map<String, GHRepository> repositories = new ConcurrentHashMap<>();
    private final Map<PrRef, GHPullRequest> pullRequests = new ConcurrentHashMap<>();
    private GitHub client;
    private HttpClient http;

    @Override
    public PullRequestData pullRequest(PrRef ref) {
        return read("pull request " + ref, () -> {
            GHPullRequest pr = repository(ref.owner(), ref.repo()).getPullRequest(ref.number());
            pullRequests.put(ref, pr);
            List<String> labels = new ArrayList<>();
            for (GHLabel label : pr.getLabels()) {
                labels.add(label.getName());
            }
            return new PullRequestData(pr.getNumber(), text(pr.getTitle()), text(pr.getBody()), login(pr.getUser()),
                    pr.getBase().getRef(), pr.getHead().getRef(), pr.getHead().getSha(), String.valueOf(pr.getHtmlUrl()),
                    List.copyOf(labels), pr.isMerged(), pr.getAdditions(), pr.getDeletions(), pr.getChangedFiles());
        });
    }

    @Override
    public Optional<String> unifiedDiff(PrRef ref) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        API + "/repos/" + ref.owner() + "/" + ref.repo() + "/pulls/" + ref.number()))
                .header("Accept", "application/vnd.github.diff")
                .header("Authorization", "Bearer " + token())
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(DIFF_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new GitHubAccessException("Cannot read the diff of " + ref + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubAccessException("Interrupted while reading the diff of " + ref, e);
        }
        return switch (response.statusCode()) {
            case 200 -> Optional.of(response.body());
            case 406 -> Optional.empty(); // GitHub does not produce diffs above its size limits
            default -> throw new GitHubAccessException("Cannot read the diff of " + ref + ": HTTP "
                    + response.statusCode() + " " + clip(response.body()), response.statusCode(), null);
        };
    }

    @Override
    public List<ChangedFile> files(PrRef ref) {
        return read("files of " + ref, () -> {
            List<ChangedFile> files = new ArrayList<>();
            for (GHPullRequestFileDetail f : pullRequestOf(ref).listFiles().withPageSize(PAGE_SIZE)) {
                files.add(new ChangedFile(f.getFilename(), f.getPreviousFilename(), f.getStatus(), f.getPatch(),
                        f.getChanges()));
                if (files.size() >= MAX_FILES) {
                    break;
                }
            }
            return List.copyOf(files);
        });
    }

    @Override
    public List<CommitInfo> commits(PrRef ref) {
        return read("commits of " + ref, () -> {
            List<CommitInfo> commits = new ArrayList<>();
            for (GHPullRequestCommitDetail c : pullRequestOf(ref).listCommits().withPageSize(PAGE_SIZE)) {
                GitUser author = c.getCommit().getAuthor();
                commits.add(new CommitInfo(c.getSha(), text(c.getCommit().getMessage()),
                        author == null ? "" : text(author.getName()), instant(author == null ? null : author.getDate())));
                if (commits.size() >= MAX_ITEMS) {
                    break;
                }
            }
            return List.copyOf(commits);
        });
    }

    @Override
    public List<CheckRun> checkRuns(String owner, String repo, String sha) {
        return read("check runs of " + owner + "/" + repo + "@" + sha, () -> {
            List<CheckRun> runs = new ArrayList<>();
            for (GHCheckRun run : repository(owner, repo).getCommit(sha).getCheckRuns().withPageSize(PAGE_SIZE)) {
                runs.add(new CheckRun(run.getName(), lower(run.getStatus()), lower(run.getConclusion())));
                if (runs.size() >= MAX_ITEMS) {
                    break;
                }
            }
            return List.copyOf(runs);
        });
    }

    @Override
    public List<Review> reviews(PrRef ref) {
        return read("reviews of " + ref, () -> {
            List<Review> reviews = new ArrayList<>();
            for (GHPullRequestReview review : pullRequestOf(ref).listReviews().withPageSize(PAGE_SIZE)) {
                reviews.add(new Review(login(review.getUser()),
                        review.getState() == null ? "" : review.getState().name(), text(review.getBody())));
                if (reviews.size() >= MAX_ITEMS) {
                    break;
                }
            }
            return List.copyOf(reviews);
        });
    }

    @Override
    public List<ReviewComment> reviewComments(PrRef ref) {
        return read("review comments of " + ref, () -> {
            List<ReviewComment> comments = new ArrayList<>();
            for (GHPullRequestReviewComment c : pullRequestOf(ref).listReviewComments().withPageSize(PAGE_SIZE)) {
                comments.add(new ReviewComment(c.getId(), Math.max(0, c.getInReplyToId()), login(c.getUser()),
                        text(c.getPath()), Math.max(0, c.getLine()), Math.max(0, c.getOriginalLine()), text(c.getBody()),
                        instant(c.getCreatedAt())));
                if (comments.size() >= MAX_ITEMS) {
                    break;
                }
            }
            return List.copyOf(comments);
        });
    }

    @Override
    public Issue issue(String owner, String repo, int number) {
        return read("issue " + owner + "/" + repo + "#" + number, () -> {
            GHIssue issue = repository(owner, repo).getIssue(number);
            return new Issue(number, text(issue.getTitle()), text(issue.getBody()));
        });
    }

    @Override
    public Contents contents(String owner, String repo, String path, String ref) {
        String normalized = path == null ? "" : path.strip().replaceFirst("^/+", "");
        String reference = ref == null || ref.isBlank() ? null : ref.strip();
        return read("file " + normalized + " of " + owner + "/" + repo + (reference == null ? "" : " at " + reference),
                () -> {
                    GHRepository repository = repository(owner, repo);
                    if (normalized.isEmpty() || normalized.endsWith("/")) {
                        return directory(normalized, repository.getDirectoryContent(normalized, reference));
                    }
                    try {
                        return new Contents(normalized, text(repository.getFileContent(normalized, reference)), null);
                    } catch (HttpException e) {
                        // a successful answer that does not read as one file is the listing of a directory
                        if (e.getResponseCode() != 200) {
                            throw e;
                        }
                    }
                    return directory(normalized, repository.getDirectoryContent(normalized, reference));
                });
    }

    @Override
    public List<CodeMatch> searchCode(String query) {
        return read("code search '" + query + "'", () -> {
            List<CodeMatch> matches = new ArrayList<>();
            for (GHContent content : client().searchContent().q(query).list().withPageSize(MAX_SEARCH_RESULTS)) {
                matches.add(new CodeMatch(content.getOwner().getFullName(), content.getPath(), content.getHtmlUrl()));
                if (matches.size() >= MAX_SEARCH_RESULTS) {
                    break;
                }
            }
            return List.copyOf(matches);
        });
    }

    @Override
    public CommitDetails commit(String owner, String repo, String sha) {
        return read("commit " + sha + " of " + owner + "/" + repo, () -> {
            GHCommit commit = repository(owner, repo).getCommit(sha);
            List<ChangedFile> files = new ArrayList<>();
            for (GHCommit.File f : commit.getFiles()) {
                files.add(new ChangedFile(f.getFileName(), f.getPreviousFilename(), f.getStatus(), f.getPatch(),
                        f.getLinesChanged()));
            }
            return new CommitDetails(info(commit), List.copyOf(files));
        });
    }

    @Override
    public List<CommitInfo> listCommits(String owner, String repo, String path, String sha) {
        return read("commits of " + owner + "/" + repo + (path == null ? "" : " at " + path), () -> {
            GHCommitQueryBuilder query = repository(owner, repo).queryCommits();
            if (path != null && !path.isBlank()) {
                query = query.path(path.strip());
            }
            if (sha != null && !sha.isBlank()) {
                query = query.from(sha.strip());
            }
            List<CommitInfo> commits = new ArrayList<>();
            for (GHCommit commit : query.pageSize(MAX_LISTED_COMMITS).list()) {
                commits.add(info(commit));
                if (commits.size() >= MAX_LISTED_COMMITS) {
                    break;
                }
            }
            return List.copyOf(commits);
        });
    }

    @Override
    public Stream<PullRequestSummary> closedPullRequests(String owner, String repo) {
        PagedIterable<GHPullRequest> pulls = read("pull requests of " + owner + "/" + repo, () -> repository(owner, repo)
                .queryPullRequests()
                .state(GHIssueState.CLOSED)
                .sort(GHPullRequestQueryBuilder.Sort.UPDATED)
                .direction(GHDirection.DESC)
                .list()
                .withPageSize(30));
        return StreamSupport.stream(pulls.spliterator(), false)
                .map(pr -> read("pull request " + owner + "/" + repo + "#" + pr.getNumber(),
                        () -> new PullRequestSummary(pr.getNumber(), text(pr.getTitle()), login(pr.getUser()))));
    }

    /** The client, built on first use; fails with an actionable message without a token. */
    synchronized GitHub client() {
        if (client == null) {
            try {
                client = new GitHubBuilder()
                        .withOAuthToken(token())
                        .withRateLimitHandler(GitHubRateLimitHandler.FAIL)
                        .withAbuseLimitHandler(GitHubAbuseLimitHandler.FAIL)
                        .build();
            } catch (IOException e) {
                throw new GitHubAccessException("Cannot create the GitHub client: " + e.getMessage(), e);
            }
        }
        return client;
    }

    private String token() {
        return token.filter(t -> !t.isBlank()).orElseThrow(() -> new GitHubAccessException(
                "No GitHub token: set GITHUB_TOKEN to a read-only fine-grained token, or log in with 'gh auth login'"));
    }

    private synchronized HttpClient http() {
        if (http == null) {
            http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return http;
    }

    private GHRepository repository(String owner, String repo) throws IOException {
        String fullName = owner + "/" + repo;
        GHRepository repository = repositories.get(fullName);
        if (repository == null) {
            repository = client().getRepository(fullName);
            repositories.put(fullName, repository);
        }
        return repository;
    }

    /** The pull request as last fetched by {@link #pullRequest}, which the reader calls first; else fetched now. */
    private GHPullRequest pullRequestOf(PrRef ref) throws IOException {
        GHPullRequest pr = pullRequests.get(ref);
        if (pr == null) {
            pr = repository(ref.owner(), ref.repo()).getPullRequest(ref.number());
            pullRequests.put(ref, pr);
        }
        return pr;
    }

    private static Contents directory(String path, List<GHContent> entries) {
        List<String> names = new ArrayList<>();
        for (GHContent entry : entries) {
            names.add(entry.getName() + (entry.isDirectory() ? "/" : ""));
        }
        return new Contents(path, null, List.copyOf(names));
    }

    /** The text of a file, cut at {@link #MAX_FILE_CHARS}; files above 1 MB are downloaded from their raw URL. */
    private static String text(GHContent content) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Reader reader = new InputStreamReader(content.read(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                sb.append(buffer, 0, read);
                if (sb.length() > MAX_FILE_CHARS) {
                    sb.setLength(MAX_FILE_CHARS);
                    sb.append(TRUNCATED);
                    break;
                }
            }
        }
        return sb.toString();
    }

    private static CommitInfo info(GHCommit commit) throws IOException {
        GHCommit.ShortInfo info = commit.getCommitShortInfo();
        GitUser author = info.getAuthor();
        return new CommitInfo(commit.getSHA1(), text(info.getMessage()), author == null ? "" : text(author.getName()),
                instant(author == null ? null : author.getDate()));
    }

    private interface Read<T> {
        T get() throws IOException;
    }

    /** Runs one read, turning hub4j's exceptions into a {@link GitHubAccessException} that names what failed. */
    private static <T> T read(String what, Read<T> body) {
        try {
            return body.get();
        } catch (GHFileNotFoundException e) {
            throw new GitHubAccessException("GitHub: " + what + " not found, or no access", 404, e);
        } catch (HttpException e) {
            throw new GitHubAccessException("GitHub: cannot read " + what + ": HTTP " + e.getResponseCode() + " "
                    + e.getMessage(), e.getResponseCode(), e);
        } catch (IOException | GHException e) {
            throw new GitHubAccessException("GitHub: cannot read " + what + ": " + e.getMessage(), status(e), e);
        }
    }

    private static int status(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof HttpException http) {
                return http.getResponseCode();
            }
        }
        return 0;
    }

    private static String login(GHUser user) throws IOException {
        return user == null ? "" : text(user.getLogin());
    }

    private static String lower(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    private static Instant instant(Date date) {
        return date == null ? null : date.toInstant();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String clip(String body) {
        String value = body == null ? "" : body.strip().replaceAll("\\s+", " ");
        return value.length() <= 200 ? value : value.substring(0, 200) + "…";
    }
}
