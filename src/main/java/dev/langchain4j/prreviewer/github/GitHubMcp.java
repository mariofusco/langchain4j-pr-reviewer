package dev.langchain4j.prreviewer.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.prreviewer.config.ReviewerConfig;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The GitHub MCP server, read-only: the local Docker image ({@code github-local}) or the remote endpoint
 * ({@code github-remote}), both configured in {@code application.properties} and started lazily, so only the selected
 * one ever runs.
 */
@ApplicationScoped
@Unremovable
public class GitHubMcp {

    public enum Transport {
        LOCAL,
        REMOTE
    }

    /** The only tools AI agents may call: reading files, searching code, reading commits and issues. */
    public static final List<String> READ_TOOLS =
            List.of("get_file_contents", "search_code", "get_commit", "list_commits", "issue_read");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    @Any
    Instance<McpClient> clients;

    @Inject
    ReviewerConfig config;

    @ConfigProperty(name = "github.token")
    Optional<String> token;

    private Transport transport;
    private McpClient client;

    /** For static agent methods, which cannot have collaborators injected. */
    public static GitHubMcp get() {
        return Arc.container().instance(GitHubMcp.class).get();
    }

    @PostConstruct
    void init() {
        transport = config.mcp();
    }

    /** Selects the server for this process; call it before the first review. */
    public synchronized void use(Transport transport) {
        if (client != null && transport != this.transport) {
            throw new IllegalStateException("The GitHub MCP client is already started with " + this.transport);
        }
        this.transport = transport;
    }

    /** The selected client, started on first use; fails fast with an actionable message when it cannot start. */
    public synchronized McpClient client() {
        if (client == null) {
            if (token.filter(t -> !t.isBlank()).isEmpty()) {
                throw new GitHubAccessException(
                        "No GitHub token: set GITHUB_TOKEN to a read-only fine-grained token, or log in with 'gh auth login'");
            }
            McpClient selected = clients.select(McpClientName.Literal.of("github-" + transport.name().toLowerCase(Locale.ROOT))).get();
            try {
                // the client needs the tool list to send tool parameters properly
                selected.listTools();
            } catch (RuntimeException e) {
                throw new GitHubAccessException("Cannot start the GitHub MCP server (" + transport + "): " + e.getMessage()
                        + (transport == Transport.LOCAL ? ". Is Docker running?" : ""), e);
            }
            client = selected;
        }
        return client;
    }

    /** A gateway calling the server's tools directly, for the deterministic fetch. */
    public GitHubGateway gateway() {
        return (tool, arguments) -> call(client(), tool, arguments);
    }

    /**
     * Only the read tools of {@link #READ_TOOLS}, for AI agents, at most {@code budget} calls per agent invocation. The
     * server starts when an agent first asks for its tools.
     */
    public ToolProvider readTools(int budget) {
        return new BudgetedToolProvider(() -> McpToolProvider.builder()
                .mcpClients(client())
                .filterToolNames(READ_TOOLS)
                .build(), budget);
    }

    static String call(McpClient client, String tool, Map<String, Object> arguments) {
        ToolExecutionResult result;
        try {
            result = client.executeTool(ToolExecutionRequest.builder()
                    .name(tool)
                    .arguments(toJson(arguments))
                    .build());
        } catch (GitHubAccessException e) {
            throw e;
        } catch (RuntimeException e) {
            // GitHub errors ("Not Found", missing permissions) arrive as tool execution exceptions
            throw new GitHubAccessException("GitHub MCP tool '" + tool + "' failed: " + e.getMessage(), e);
        }
        if (result.isError()) {
            throw new GitHubAccessException("GitHub MCP tool '" + tool + "' failed: " + result.resultText());
        }
        return result.resultText();
    }

    private static String toJson(Map<String, Object> arguments) {
        try {
            return JSON.writeValueAsString(arguments);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
