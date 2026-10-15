package dev.langchain4j.prreviewer.model;

import java.util.Locale;

/** The chat model provider; each tier maps to a named model configuration in {@code application.properties}. */
public enum ModelProvider {
    ANTHROPIC("ANTHROPIC_API_KEY"),
    OPENAI("OPENAI_API_KEY");

    private final String apiKeyVariable;

    ModelProvider(String apiKeyVariable) {
        this.apiKeyVariable = apiKeyVariable;
    }

    /** The environment variable holding the provider's API key. */
    public String apiKeyVariable() {
        return apiKeyVariable;
    }

    /** The named model configuration of a tier, for instance {@code anthropic-economy}. */
    public String configName(ModelTier tier) {
        return name().toLowerCase(Locale.ROOT) + "-" + tier.name().toLowerCase(Locale.ROOT);
    }

    /** The named configuration of the tier's variant for tool-using agents, when the provider defines one. */
    public String toolConfigName(ModelTier tier) {
        return configName(tier) + "-tools";
    }
}
