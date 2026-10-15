package dev.langchain4j.prreviewer.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
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
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Checks, against a local server standing in for the Chat Completions API, what the configured OpenAI models send:
 * reasoning effort and completion budget per tier, strict JSON schema output, no sampling parameters, and cached
 * prompt tokens priced at the cached rate.
 */
class OpenAiRequestShapeTest {

    static final ObjectMapper JSON = new ObjectMapper();

    HttpServer server;
    final List<JsonNode> requests = new ArrayList<>();
    final Deque<String> responses = new ArrayDeque<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
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
        String read(@UserMessage String request);
    }

    private ChatModel model(ModelTier tier, CostLedger ledger) {
        String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1/";
        return new OpenAiModels("test-key", baseUrl, Map.of(), List.of(new CostTrackingListener(ledger))).model(tier);
    }

    private static String response(String model, String content) {
        return """
                {"id":"chatcmpl-1","object":"chat.completion","created":1,"model":"%s",
                 "choices":[{"index":0,"message":{"role":"assistant","content":%s},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":1200,"completion_tokens":80,"total_tokens":1280,
                          "prompt_tokens_details":{"cached_tokens":500},
                          "completion_tokens_details":{"reasoning_tokens":40}}}
                """.formatted(model, JSON.valueToTree(content).toString());
    }

    @Test
    void economy_tier_sends_strict_schema_low_effort_and_prices_cached_tokens() {
        responses.add(response("gpt-5-nano-2025-08-07", "{\"intent\":\"Refactor\",\"category\":\"REFACTOR\",\"size\":\"SMALL\","
                + "\"risk\":\"LOW\",\"areasTouched\":[],\"needsContext\":false,\"openQuestions\":[]}"));
        CostLedger ledger = new CostLedger();

        PrSummary result = AiServices.create(Triage.class, model(ModelTier.ECONOMY, ledger)).triage("a pull request");

        assertThat(result.category()).isEqualTo(Category.REFACTOR);
        JsonNode request = requests.get(0);
        assertThat(request.path("model").asText()).isEqualTo("gpt-5-nano");
        assertThat(request.path("reasoning_effort").asText()).isEqualTo("low");
        assertThat(request.path("max_completion_tokens").asInt()).isEqualTo(16_000);
        assertThat(request.has("max_tokens")).isFalse();
        assertThat(request.has("temperature")).isFalse();
        assertThat(request.has("top_p")).isFalse();
        JsonNode format = request.path("response_format");
        assertThat(format.path("type").asText()).isEqualTo("json_schema");
        assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(format.path("json_schema").path("schema").path("properties").has("category")).isTrue();

        // 700 uncached input tokens at $0.05, 500 cached at $0.005, 80 output at $0.40, per million
        assertThat(ledger.entries()).singleElement().satisfies(e -> {
            assertThat(e.model()).isEqualTo("gpt-5-nano-2025-08-07");
            assertThat(e.inputTokens()).isEqualTo(700);
            assertThat(e.cacheReadTokens()).isEqualTo(500);
            assertThat(e.costUsd()).isCloseTo((700 * 0.05 + 500 * 0.005 + 80 * 0.40) / 1_000_000, within(1e-12));
        });
    }

    @Test
    void standard_and_premium_tiers_use_luna_and_full_models_with_more_effort_and_budget() {
        responses.add(response("gpt-5.6-luna", "ok"));
        responses.add(response("gpt-5.1-2025-11-13", "ok"));

        AiServices.create(Reader.class, model(ModelTier.STANDARD, new CostLedger())).read("hello");
        AiServices.create(Reader.class, model(ModelTier.PREMIUM, new CostLedger())).read("hello");

        assertThat(requests.get(0).path("model").asText()).isEqualTo("gpt-5.6-luna");
        assertThat(requests.get(0).path("reasoning_effort").asText()).isEqualTo("medium");
        assertThat(requests.get(0).path("max_completion_tokens").asInt()).isEqualTo(64_000);
        assertThat(requests.get(0).has("response_format")).isFalse();
        assertThat(requests.get(1).path("model").asText()).isEqualTo("gpt-5.1");
        assertThat(requests.get(1).path("reasoning_effort").asText()).isEqualTo("high");
    }

    @Test
    void tool_variants_drop_reasoning_on_models_that_reject_tools_with_effort() {
        responses.add(response("gpt-5.6-luna", "ok"));
        responses.add(response("gpt-5-nano-2025-08-07", "ok"));
        String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1/";
        OpenAiModels models = new OpenAiModels("k", baseUrl, Map.of(), List.of());

        AiServices.create(Reader.class, models.toolModel(ModelTier.STANDARD)).read("hello");
        AiServices.create(Reader.class, models.toolModel(ModelTier.ECONOMY)).read("hello");

        assertThat(requests.get(0).path("model").asText()).isEqualTo("gpt-5.6-luna");
        assertThat(requests.get(0).path("reasoning_effort").asText()).isEqualTo("none");
        assertThat(requests.get(1).path("model").asText()).isEqualTo("gpt-5-nano");
        assertThat(requests.get(1).path("reasoning_effort").asText()).isEqualTo("low");
        assertThat(OpenAiModels.toolReasoningEffort(ModelTier.PREMIUM, "gpt-5.1")).isEqualTo("none");
        assertThat(OpenAiModels.toolReasoningEffort(ModelTier.STANDARD, "gpt-5-mini")).isEqualTo("medium");
    }

    @Test
    void tier_models_can_be_overridden() {
        responses.add(response("gpt-5-mini-2025-08-07", "ok"));
        String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1/";
        ChatModel standard = new OpenAiModels("k", baseUrl, Map.of(ModelTier.STANDARD, "gpt-5-mini"), List.of())
                .model(ModelTier.STANDARD);

        AiServices.create(Reader.class, standard).read("hello");

        assertThat(requests.get(0).path("model").asText()).isEqualTo("gpt-5-mini");
    }
}
