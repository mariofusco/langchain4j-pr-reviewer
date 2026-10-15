package dev.langchain4j.prreviewer.github;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.prreviewer.AppContext;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.PullRequest;

/** Non-AI agent: fetches the pull request through the GitHub MCP server. */
public class PrFetcher {

    @Agent(name = "prFetcher",
            description = "Fetches the pull request, its diff, commits, checks, reviews and linked issue from GitHub",
            typedOutputKey = Keys.PullRequest.class)
    public static PullRequest fetch(@K(Keys.PrUrl.class) String prUrl) {
        return new PullRequestReader(AppContext.get().github()).read(PrUrl.parse(prUrl));
    }
}
