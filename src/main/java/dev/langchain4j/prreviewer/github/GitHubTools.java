package dev.langchain4j.prreviewer.github;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.prreviewer.github.GitHubReads.ChangedFile;
import dev.langchain4j.prreviewer.github.GitHubReads.CodeMatch;
import dev.langchain4j.prreviewer.github.GitHubReads.CommitDetails;
import dev.langchain4j.prreviewer.github.GitHubReads.CommitInfo;
import dev.langchain4j.prreviewer.github.GitHubReads.Contents;
import dev.langchain4j.prreviewer.github.GitHubReads.Issue;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

/**
 * The five read-only GitHub tools of the AI agents, offered with {@code @ToolBox(GitHubTools.class)} under the names
 * the prompts use. Quarkus generates their executors at build time and bounds the calls of each agent invocation with
 * the {@link dev.langchain4j.prreviewer.agents.ToolBudget} wrapper. A GitHub error reaches the model as text, the
 * extension's default for tool errors, so that it can correct a wrong path or sha. A singleton: the agentic extension
 * hands the bean instance itself to the agent, and a singleton has no client proxy in front of it.
 */
@Singleton
@Unremovable
public class GitHubTools {

    public static final List<String> TOOL_NAMES =
            List.of("get_file_contents", "search_code", "get_commit", "list_commits", "issue_read");

    @Inject
    GitHubReads github;

    @Tool(name = "get_file_contents",
            value = "Reads a file of a GitHub repository as of a commit, or lists the entries of a directory")
    public String getFileContents(@P("Repository owner") String owner,
                                  @P("Repository name") String repo,
                                  @P("Path of the file or directory in the repository, without a leading slash") String path,
                                  @P(value = "Commit sha, branch or tag; use the head sha of the pull request", required = false) String sha) {
        Contents contents = github.contents(owner, repo, path, sha);
        if (!contents.directory()) {
            return contents.text();
        }
        return contents.entries().isEmpty()
                ? "The directory " + contents.path() + " is empty"
                : "Entries of " + contents.path() + ":\n" + String.join("\n", contents.entries());
    }

    @Tool(name = "search_code",
            value = "Searches code on GitHub with the code search syntax, for instance \"methodName repo:owner/repo\"; "
                    + "returns the matching files, not the matching lines")
    public String searchCode(@P("Search query") String query) {
        List<CodeMatch> matches = github.searchCode(query);
        if (matches.isEmpty()) {
            return "No file matches " + query;
        }
        StringBuilder sb = new StringBuilder(matches.size() + " matching files:\n");
        for (CodeMatch match : matches) {
            sb.append(match.path()).append(" in ").append(match.repository()).append('\n');
        }
        return sb.toString();
    }

    @Tool(name = "get_commit", value = "Reads a commit: its message, author and changed files with their patches")
    public String getCommit(@P("Repository owner") String owner,
                            @P("Repository name") String repo,
                            @P("Commit sha") String sha) {
        CommitDetails details = github.commit(owner, repo, sha);
        StringBuilder sb = new StringBuilder("commit ").append(details.commit().sha()).append("\nAuthor: ")
                .append(details.commit().author()).append("\n\n").append(details.commit().message()).append("\n\n");
        for (ChangedFile file : details.files()) {
            sb.append("--- ").append(file.filename()).append(" (").append(file.status()).append(", ")
                    .append(file.changes()).append(" lines changed)\n");
            if (file.patch() != null && !file.patch().isEmpty()) {
                sb.append(file.patch()).append('\n');
            }
        }
        return sb.toString();
    }

    @Tool(name = "list_commits",
            value = "Lists the most recent commits of a repository, those touching one path when a path is given, "
                    + "starting from a sha or branch when one is given")
    public String listCommits(@P("Repository owner") String owner,
                              @P("Repository name") String repo,
                              @P(value = "Path of a file or directory", required = false) String path,
                              @P(value = "Sha or branch to start from", required = false) String sha) {
        List<CommitInfo> commits = github.listCommits(owner, repo, path, sha);
        if (commits.isEmpty()) {
            return "No commits found";
        }
        StringBuilder sb = new StringBuilder();
        for (CommitInfo commit : commits) {
            sb.append(commit.sha(), 0, Math.min(7, commit.sha().length())).append(' ')
                    .append(commit.toCommit().headline()).append(" (").append(commit.author()).append(")\n");
        }
        return sb.toString();
    }

    @Tool(name = "issue_read", value = "Reads an issue of a GitHub repository: its title and body")
    public String issueRead(@P("Repository owner") String owner,
                            @P("Repository name") String repo,
                            @P("Issue number") int issueNumber) {
        Issue issue = github.issue(owner, repo, issueNumber);
        return "#" + issue.number() + " " + issue.title() + "\n\n" + issue.body();
    }
}
