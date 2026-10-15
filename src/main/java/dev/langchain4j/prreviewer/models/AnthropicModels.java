package dev.langchain4j.prreviewer.models;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.prreviewer.model.ModelTier;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Anthropic models of the three tiers, built once and shared by all agents.
 * <ul>
 *   <li>thinking is left to its default (adaptive on the 5.x models) and thinking blocks are returned, so that
 *   tool-using agents pass them back unchanged in their tool loops;</li>
 *   <li>no sampling parameters, which the 5.x models reject;</li>
 *   <li>effort is set per tier through {@link EffortHttpClientBuilder}, because custom parameters would add a second
 *   {@code output_config} next to the one carrying the structured output format;</li>
 *   <li>system messages and growing conversations are cached: reviewers on the same model share their short system
 *   prompt, and tool-using agents reuse the prefix of their own conversation on every tool round trip.</li>
 * </ul>
 */
public final class AnthropicModels implements ChatModels {

    public static final String HAIKU = "claude-haiku-5-5";
    public static final String SONNET = "claude-sonnet-5-5";
    public static final String OPUS = "claude-opus-5-5";

    /** Output budget, thinking included: Haiku answers are short, Sonnet and Opus need room on large diffs. */
    static final int ECONOMY_MAX_TOKENS = 16_000;
    static final int MAX_TOKENS = 32_000;

    private final Map<ModelTier, ChatModel> models = new EnumMap<>(ModelTier.class);

    public AnthropicModels(String apiKey, String baseUrl, List<ChatModelListener> listeners) {
        for (ModelTier tier : ModelTier.values()) {
            models.put(tier, build(apiKey, baseUrl, tier, listeners));
        }
    }

    /** Uses {@code ANTHROPIC_API_KEY} and, when set, {@code ANTHROPIC_BASE_URL}. */
    public static AnthropicModels fromEnvironment(List<ChatModelListener> listeners) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not set");
        }
        return new AnthropicModels(apiKey, System.getenv("ANTHROPIC_BASE_URL"), listeners);
    }

    @Override
    public ChatModel model(ModelTier tier) {
        return models.get(tier);
    }

    public static String modelName(ModelTier tier) {
        return switch (tier) {
            case ECONOMY -> HAIKU;
            case STANDARD -> SONNET;
            case PREMIUM -> OPUS;
        };
    }

    public static String effort(ModelTier tier) {
        return switch (tier) {
            case ECONOMY -> "low";
            case STANDARD -> "medium";
            case PREMIUM -> "high";
        };
    }

    static AnthropicChatModel build(String apiKey, String baseUrl, ModelTier tier, List<ChatModelListener> listeners) {
        AnthropicChatModel.AnthropicChatModelBuilder builder = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName(tier))
                .maxTokens(tier == ModelTier.ECONOMY ? ECONOMY_MAX_TOKENS : MAX_TOKENS)
                .returnThinking(true)
                .sendThinking(true)
                .cacheSystemMessages(true)
                .cacheAutomatically(true)
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .httpClientBuilder(new EffortHttpClientBuilder(JdkHttpClient.builder(), effort(tier)))
                .timeout(Duration.ofMinutes(10))
                .maxRetries(2)
                .listeners(listeners);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}
