package dev.langchain4j.prreviewer.github;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.PullRequest;

/** Non-AI agent: fetches the pull request from GitHub. */
public class PrFetcher {

    @Agent(name = "prFetcher",
            description = "Fetches the pull request, its diff, commits, checks, reviews and linked issue from GitHub",
            outputKey = Keys.PULL_REQUEST)
    public static PullRequest fetch(String prUrl) {
        return new PullRequestReader(GitHubReads.get()).read(PrUrl.parse(prUrl));
    }
}
