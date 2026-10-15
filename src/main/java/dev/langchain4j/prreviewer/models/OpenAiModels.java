package dev.langchain4j.prreviewer.models;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.prreviewer.model.ModelTier;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The OpenAI models of the three tiers: the cheapest models of each class that can reasonably do the job, as priced
 * in October 2026.
 * <ul>
 *   <li>{@code gpt-5-nano} ($0.05 / $0.40 per million tokens) for classification, checklists and the critic;</li>
 *   <li>{@code gpt-5.6-luna} ($0.20 / $1.20) for code judgment, scoring and writing;</li>
 *   <li>{@code gpt-5.1} ($1.25 / $10.00) as the escalation tier.</li>
 * </ul>
 * All three are reasoning models: effort is set per tier with {@code reasoning_effort}, the output budget with
 * {@code max_completion_tokens} (reasoning included), no sampling parameters are sent, and structured outputs use
 * strict JSON schemas. Prompt caching is automatic on OpenAI for prompts above 1024 tokens. Each tier's model can be
 * overridden with {@code OPENAI_ECONOMY_MODEL}, {@code OPENAI_STANDARD_MODEL} and {@code OPENAI_PREMIUM_MODEL}, for
 * instance to use {@code gpt-5-mini} ($0.25 / $2.00) as the standard tier.
 */
public final class OpenAiModels implements ChatModels {

    public static final String NANO = "gpt-5-nano";
    public static final String LUNA = "gpt-5.6-luna";
    public static final String FULL = "gpt-5.1";

    /**
     * Completion budgets, reasoning included. Measured: a correctness review of a 300-line diff on gpt-5.1 at high
     * effort used about 30K completion tokens, so the standard and premium tiers get 64K; the budget is a cap, not a cost.
     */
    static final int ECONOMY_MAX_TOKENS = 16_000;
    static final int MAX_TOKENS = 64_000;

    private final Map<ModelTier, ChatModel> models = new EnumMap<>(ModelTier.class);
    private final Map<ModelTier, ChatModel> toolModels = new EnumMap<>(ModelTier.class);

    public OpenAiModels(String apiKey, String baseUrl, Map<ModelTier, String> modelNames, List<ChatModelListener> listeners) {
        for (ModelTier tier : ModelTier.values()) {
            String modelName = modelNames.getOrDefault(tier, defaultModelName(tier));
            models.put(tier, build(apiKey, baseUrl, modelName, tier, reasoningEffort(tier), listeners));
            toolModels.put(tier, build(apiKey, baseUrl, modelName, tier, toolReasoningEffort(tier, modelName), listeners));
        }
    }

    /** Uses {@code OPENAI_API_KEY}, and when set {@code OPENAI_BASE_URL} and the per-tier model overrides. */
    public static OpenAiModels fromEnvironment(List<ChatModelListener> listeners) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not set");
        }
        Map<ModelTier, String> names = new EnumMap<>(ModelTier.class);
        for (ModelTier tier : ModelTier.values()) {
            String override = System.getenv("OPENAI_" + tier.name() + "_MODEL");
            names.put(tier, override == null || override.isBlank() ? defaultModelName(tier) : override.strip());
        }
        return new OpenAiModels(apiKey, System.getenv("OPENAI_BASE_URL"), names, listeners);
    }

    @Override
    public ChatModel model(ModelTier tier) {
        return models.get(tier);
    }

    @Override
    public ChatModel toolModel(ModelTier tier) {
        return toolModels.get(tier);
    }

    public static String defaultModelName(ModelTier tier) {
        return switch (tier) {
            case ECONOMY -> NANO;
            case STANDARD -> LUNA;
            case PREMIUM -> FULL;
        };
    }

    public static String reasoningEffort(ModelTier tier) {
        return switch (tier) {
            case ECONOMY -> "low";
            case STANDARD -> "medium";
            case PREMIUM -> "high";
        };
    }

    /**
     * The reasoning effort for agents that call tools. The chat completions endpoint rejects function tools together
     * with a reasoning effort on the gpt-5.6 and newer small models, and asks for {@code none} instead; the gpt-5 nano
     * and mini models have no {@code none} level but accept tools with any effort.
     */
    static String toolReasoningEffort(ModelTier tier, String modelName) {
        return supportsNoReasoning(modelName) ? "none" : reasoningEffort(tier);
    }

    private static boolean supportsNoReasoning(String modelName) {
        return !(modelName.startsWith("gpt-5-") || modelName.equals("gpt-5") || modelName.startsWith("gpt-4"));
    }

    static OpenAiChatModel build(String apiKey, String baseUrl, String modelName, ModelTier tier, String reasoningEffort,
                                 List<ChatModelListener> listeners) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .reasoningEffort(reasoningEffort)
                .maxCompletionTokens(tier == ModelTier.ECONOMY ? ECONOMY_MAX_TOKENS : MAX_TOKENS)
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .strictJsonSchema(true)
                .timeout(Duration.ofMinutes(10))
                .maxRetries(2)
                .listeners(listeners);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}
