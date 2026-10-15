package dev.langchain4j.prreviewer.support;

import dev.langchain4j.prreviewer.github.GitHubMcp;
import dev.langchain4j.prreviewer.models.DecisionModels;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Conditions of the integration tests: which external services are reachable from here. */
public final class Availability {

    private Availability() {}

    public static boolean githubMcp() {
        try {
            GitHubMcp.resolveToken();
            Process docker = new ProcessBuilder("docker", "image", "inspect", GitHubMcp.IMAGE).start();
            return docker.waitFor(20, TimeUnit.SECONDS) && docker.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean localDecisionModel() {
        String base = System.getenv().getOrDefault("SYSTEM_ONE_BASE_URL", DecisionModels.LOCAL_BASE_URL);
        String model = System.getenv().getOrDefault("SYSTEM_ONE_MODEL_NAME", DecisionModels.LOCAL_MODEL);
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
