package dev.langchain4j.prreviewer.support;

import dev.langchain4j.prreviewer.github.GitHubGateway;
import dev.langchain4j.prreviewer.github.GitHubMcp;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkus.test.Mock;
import jakarta.inject.Inject;
import java.util.Map;

/** GitHub from the scenario's fixtures; AI agents get no tools, so no MCP server ever starts. */
@Mock
public class FixtureGitHubMcp extends GitHubMcp {

    @Inject
    TestScenario scenario;

    @Override
    public GitHubGateway gateway() {
        return scenario.github();
    }

    @Override
    public ToolProvider readTools(int budget) {
        return request -> new ToolProviderResult(Map.of());
    }
}
