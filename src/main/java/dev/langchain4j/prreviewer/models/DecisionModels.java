package dev.langchain4j.prreviewer.models;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.openai.OpenAiDecisionModel;
import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;
import java.time.Duration;

/** The decision model behind the reviewer router, speaking the System One API. */
public final class DecisionModels {

    public enum Provider {
        /** A local System One server, by default Ollama serving {@code tev1:0.8b}. */
        LOCAL,
        /** The hosted TypeSafe API. */
        TYPESAFE,
        /** TypeSafe models through OpenRouter. */
        OPENROUTER,
        /** The OpenAI Decisions API. */
        OPENAI
    }

    public static final String LOCAL_BASE_URL = "http://localhost:11434";
    public static final String LOCAL_MODEL = "tev1:0.8b";

    /**
     * Default activation threshold of the reviewer router. Measured on five synthetic changes: the hosted models
     * separate relevant from irrelevant reviewers around 0.5, the small local model peaks around 0.2.
     */
    public static double defaultRouterThreshold(Provider provider) {
        return provider == Provider.LOCAL ? 0.2 : 0.5;
    }

    private DecisionModels() {}

    public static DecisionModel create(Provider provider, DecisionModelListener listener) {
        if (provider == Provider.OPENAI) {
            return OpenAiDecisionModel.builder()
                    .apiKey(required("OPENAI_API_KEY"))
                    .modelName(env("OPENAI_DECISION_MODEL", "gpt-6-luna"))
                    .timeout(Duration.ofSeconds(60))
                    .maxRetries(2)
                    .listeners(listener)
                    .build();
        }
        TypeSafeDecisionModel.TypeSafeDecisionModelBuilder builder = TypeSafeDecisionModel.builder()
                .timeout(Duration.ofSeconds(60))
                .maxRetries(2)
                .listeners(listener);
        switch (provider) {
            case LOCAL -> builder
                    .baseUrl(env("SYSTEM_ONE_BASE_URL", LOCAL_BASE_URL))
                    .modelName(env("SYSTEM_ONE_MODEL_NAME", LOCAL_MODEL));
            case TYPESAFE -> builder
                    .apiKey(required("TYPESAFE_API_KEY"))
                    .modelName(env("TYPESAFE_MODEL_NAME", "jev-latest"));
            case OPENROUTER -> builder
                    .baseUrl("https://openrouter.ai/api/")
                    .apiKey(required("OPENROUTER_API_KEY"))
                    .modelName(env("OPENROUTER_DECISION_MODEL", "typesafe/jev-1.13"));
            case OPENAI -> throw new IllegalStateException("handled above");
        }
        return builder.build();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
