package dev.langchain4j.prreviewer.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.model.Category;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.observability.CostLedger;
import dev.langchain4j.prreviewer.observability.CostTrackingListener;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Checks, against a local server standing in for the Messages API, what the configured Anthropic models send:
 * effort and structured output share one output_config, no sampling parameters, cached system prompt, and thinking
 * blocks passed back unchanged in tool loops.
 */
class AnthropicRequestShapeTest {

    static final ObjectMapper JSON = new ObjectMapper();

    HttpServer server;
    final List<JsonNode> requests = new ArrayList<>();
    final Deque<String> responses = new ArrayDeque<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            requests.add(JSON.readTree(exchange.getRequestBody().readAllBytes()));
            byte[] body = responses.poll().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    interface Triage {
        @SystemMessage("You triage pull requests.")
        @UserMessage("Triage this: {{it}}")
        PrSummary triage(String pr);
    }

    interface Reader {
        @SystemMessage("You read files.")
        String read(@UserMessage String request);
    }

    static class Files {
        @Tool("Reads a file")
        String readFile(String path) {
            return "content of " + path;
        }
    }

    private ChatModel model(ModelTier tier, CostLedger ledger) {
        String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1/";
        return new AnthropicModels("test-key", baseUrl, List.of(new CostTrackingListener(ledger))).model(tier);
    }

    private static String response(String model, String contentJson, String stopReason) {
        return """
                {"id":"msg_1","type":"message","role":"assistant","model":"%s","content":%s,"stop_reason":"%s",
                 "usage":{"input_tokens":1200,"output_tokens":80,"cache_read_input_tokens":500,"cache_creation_input_tokens":0}}
                """.formatted(model, contentJson, stopReason);
    }

    @Test
    void structured_output_and_effort_share_the_output_config() {
        String summary = "{\\\"intent\\\":\\\"Refactor\\\",\\\"category\\\":\\\"REFACTOR\\\",\\\"size\\\":\\\"SMALL\\\","
                + "\\\"risk\\\":\\\"LOW\\\",\\\"areasTouched\\\":[],\\\"needsContext\\\":false,\\\"openQuestions\\\":[]}";
        responses.add(response(AnthropicModels.HAIKU,
                "[{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"sig-1\"},{\"type\":\"text\",\"text\":\""
                        + summary + "\"}]", "end_turn"));
        CostLedger ledger = new CostLedger();

        PrSummary result = AiServices.create(Triage.class, model(ModelTier.ECONOMY, ledger)).triage("a pull request");

        assertThat(result.category()).isEqualTo(Category.REFACTOR);
        JsonNode request = requests.get(0);
        assertThat(request.path("model").asText()).isEqualTo("claude-haiku-5-5");
        assertThat(request.path("output_config").path("effort").asText()).isEqualTo("low");
        assertThat(request.path("output_config").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(request.path("output_config").path("format").path("schema").path("properties").has("category"))
                .isTrue();
        assertThat(request.has("temperature")).isFalse();
        assertThat(request.has("top_p")).isFalse();
        assertThat(request.has("thinking")).isFalse(); // adaptive by default on the 5.x models
        assertThat(request.path("max_tokens").asInt()).isEqualTo(16_000);
        assertThat(request.path("system").toString()).contains("cache_control");

        assertThat(ledger.entries()).singleElement().satisfies(e -> {
            assertThat(e.model()).isEqualTo("claude-haiku-5-5");
            assertThat(e.cacheReadTokens()).isEqualTo(500);
            assertThat(e.costUsd()).isGreaterThan(0.0);
        });
    }

    @Test
    void premium_tier_uses_opus_with_high_effort() {
        responses.add(response(AnthropicModels.OPUS, "[{\"type\":\"text\",\"text\":\"done\"}]", "end_turn"));

        AiServices.create(Reader.class, model(ModelTier.PREMIUM, new CostLedger())).read("hello");

        assertThat(requests.get(0).path("model").asText()).isEqualTo("claude-opus-5-5");
        assertThat(requests.get(0).path("output_config").path("effort").asText()).isEqualTo("high");
        assertThat(requests.get(0).path("output_config").has("format")).isFalse();
        assertThat(requests.get(0).path("max_tokens").asInt()).isEqualTo(32_000);
    }

    @Test
    void thinking_blocks_are_passed_back_unchanged_in_tool_loops() {
        responses.add(response(AnthropicModels.SONNET, """
                [{"type":"thinking","thinking":"","signature":"sig-1"},
                 {"type":"tool_use","id":"toolu_1","name":"readFile","input":{"path":"pom.xml"}}]""", "tool_use"));
        responses.add(response(AnthropicModels.SONNET, """
                [{"type":"thinking","thinking":"","signature":"sig-2"},{"type":"text","text":"The pom is fine."}]""",
                "end_turn"));

        Reader reader = AiServices.builder(Reader.class)
                .chatModel(model(ModelTier.STANDARD, new CostLedger()))
                .tools(new Files())
                .build();
        String answer = reader.read("Read pom.xml");

        assertThat(answer).isEqualTo("The pom is fine.");
        assertThat(requests).hasSize(2);
        JsonNode replayed = requests.get(1).path("messages").get(1);
        assertThat(replayed.path("role").asText()).isEqualTo("assistant");
        assertThat(replayed.path("content").get(0).path("type").asText()).isEqualTo("thinking");
        assertThat(replayed.path("content").get(0).path("signature").asText()).isEqualTo("sig-1");
        assertThat(replayed.path("content").get(1).path("type").asText()).isEqualTo("tool_use");
        assertThat(requests.get(1).path("output_config").path("effort").asText()).isEqualTo("medium");
    }
}
