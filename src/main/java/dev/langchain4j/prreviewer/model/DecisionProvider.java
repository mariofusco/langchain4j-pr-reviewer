package dev.langchain4j.prreviewer.model;

/** The decision model behind the reviewer router; each maps to a named typesafe configuration. */
public enum DecisionProvider {
    /** System One API on a local server (Ollama serving {@code tev1:0.8b}), no key. */
    LOCAL("local", null, 0.2),
    /** Hosted TypeSafe API. */
    TYPESAFE("hosted", "TYPESAFE_API_KEY", 0.5),
    /** TypeSafe models through OpenRouter. */
    OPENROUTER("openrouter", "OPENROUTER_API_KEY", 0.5);

    private final String configName;
    private final String apiKeyVariable;
    private final double defaultRouterThreshold;

    DecisionProvider(String configName, String apiKeyVariable, double defaultRouterThreshold) {
        this.configName = configName;
        this.apiKeyVariable = apiKeyVariable;
        this.defaultRouterThreshold = defaultRouterThreshold;
    }

    public String configName() {
        return configName;
    }

    /** The environment variable holding the key, or null when none is needed. */
    public String apiKeyVariable() {
        return apiKeyVariable;
    }

    /**
     * Default activation threshold of the reviewer router. Measured on five synthetic changes: the hosted models
     * separate relevant from irrelevant reviewers clearly at 0.5; the small local model is best around 0.2.
     */
    public double defaultRouterThreshold() {
        return defaultRouterThreshold;
    }
}
