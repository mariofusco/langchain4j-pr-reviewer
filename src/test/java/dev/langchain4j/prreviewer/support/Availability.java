package dev.langchain4j.prreviewer.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Conditions of the integration tests: which external services are reachable from here. */
public final class Availability {

    /** The image of the local GitHub MCP server, as configured in {@code application.properties}. */
    static final String MCP_IMAGE = "ghcr.io/github/github-mcp-server";

    private Availability() {}

    /** The GitHub token as the application resolves it: {@code GITHUB_TOKEN}, else {@code gh auth token}. */
    public static boolean githubToken() {
        String token = System.getenv("GITHUB_TOKEN");
        if (token != null && !token.isBlank()) {
            return true;
        }
        try {
            Process gh = new ProcessBuilder("gh", "auth", "token").start();
            return gh.waitFor(10, TimeUnit.SECONDS) && gh.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean githubMcp() {
        try {
            if (!githubToken()) {
                return false;
            }
            Process docker = new ProcessBuilder("docker", "image", "inspect", MCP_IMAGE).start();
            return docker.waitFor(20, TimeUnit.SECONDS) && docker.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean localDecisionModel() {
        String base = System.getenv().getOrDefault("SYSTEM_ONE_BASE_URL", "http://localhost:11434");
        String model = System.getenv().getOrDefault("SYSTEM_ONE_MODEL_NAME", "tev1:0.8b");
        try {
            HttpResponse<String> tags = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + "/api/tags"))
                    .timeout(Duration.ofSeconds(3)).build(), HttpResponse.BodyHandlers.ofString());
            return tags.statusCode() == 200 && tags.body().contains("\"" + model + "\"");
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean anthropic() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        return key != null && !key.isBlank() && githubMcp() && localDecisionModel();
    }

    public static boolean openAi() {
        String key = System.getenv("OPENAI_API_KEY");
        return key != null && !key.isBlank() && githubMcp() && localDecisionModel();
    }
}
