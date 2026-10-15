package dev.langchain4j.prreviewer.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Connects to the GitHub MCP server, read-only, locally through Docker or remotely over HTTP. */
public final class GitHubMcp {

    public enum Transport {
        LOCAL,
        REMOTE
    }

    public static final String IMAGE = "ghcr.io/github/github-mcp-server";
    public static final String REMOTE_URL = "https://api.githubcopilot.com/mcp/";

    /** The only tools AI agents may call: reading files, searching code, reading commits and issues. */
    public static final List<String> READ_TOOLS =
            List.of("get_file_contents", "search_code", "get_commit", "list_commits", "issue_read");

    private static final ObjectMapper JSON = new ObjectMapper();

    private GitHubMcp() {}

    public static McpClient client(Transport transport, String token) {
        McpTransport mcpTransport = switch (transport) {
            case LOCAL -> StdioMcpTransport.builder()
                    .command(List.of("docker", "run", "-i", "--rm",
                            "-e", "GITHUB_PERSONAL_ACCESS_TOKEN",
                            "-e", "GITHUB_READ_ONLY=1",
                            "-e", "GITHUB_TOOLSETS=repos,pull_requests,issues",
                            IMAGE))
                    .environment(Map.of("GITHUB_PERSONAL_ACCESS_TOKEN", token))
                    .build();
            case REMOTE -> StreamableHttpMcpTransport.builder()
                    .url(REMOTE_URL)
                    .customHeaders(Map.of("Authorization", "Bearer " + token))
                    .timeout(Duration.ofSeconds(60))
                    .build();
        };
        McpClient client = DefaultMcpClient.builder()
                .key("github")
                .transport(mcpTransport)
                .toolExecutionTimeout(Duration.ofSeconds(60))
                .cacheToolList(true)
                .build();
        // fetch the tool list once up front: the client needs it to send tool parameters properly, and it fails fast
        // when the server cannot start (Docker missing, bad token)
        client.listTools();
        return client;
    }

    /** The GitHub token: {@code GITHUB_TOKEN}, else the token of the GitHub CLI. */
    public static String resolveToken() {
        String token = System.getenv("GITHUB_TOKEN");
        if (token != null && !token.isBlank()) {
            return token.strip();
        }
        try {
            Process process = new ProcessBuilder("gh", "auth", "token").redirectErrorStream(false).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0 && !output.isBlank()) {
                return output;
            }
        } catch (IOException e) {
            // gh not installed: fall through
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new IllegalStateException(
                "No GitHub token: set GITHUB_TOKEN to a read-only fine-grained token, or log in with 'gh auth login'");
    }

    /** Only the read tools of {@link #READ_TOOLS}, for AI agents. */
    public static ToolProvider readTools(McpClient client) {
        return McpToolProvider.builder()
                .mcpClients(client)
                .filterToolNames(READ_TOOLS)
                .build();
    }

    /** A gateway calling tools of the given client directly, for the deterministic fetch. */
    public static GitHubGateway gateway(McpClient client) {
        return (tool, arguments) -> {
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
        };
    }

    private static String toJson(Map<String, Object> arguments) {
        try {
            return JSON.writeValueAsString(arguments);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
