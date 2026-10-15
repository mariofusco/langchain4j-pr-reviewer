package dev.langchain4j.prreviewer.models;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.model.ModelProvider;
import dev.langchain4j.prreviewer.model.ModelTier;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * The chat models of every provider and tier, configured in {@code application.properties} as named models
 * ({@code anthropic-economy}, ..., {@code openai-premium-tools}).
 */
@ApplicationScoped
@Unremovable
public class ModelCatalog {

    @Inject
    @Any
    Instance<ChatModel> models;

    public static ModelCatalog get() {
        return Arc.container().instance(ModelCatalog.class).get();
    }

    public ChatModel chatModel(ModelProvider provider, ModelTier tier) {
        return models.select(ModelName.Literal.of(provider.configName(tier))).get();
    }

    /** The tier's variant for tool-using agents when the provider configures one, else the tier's model. */
    public ChatModel toolModel(ModelProvider provider, ModelTier tier) {
        Instance<ChatModel> tools = models.select(ModelName.Literal.of(provider.toolConfigName(tier)));
        return tools.isResolvable() ? tools.get() : chatModel(provider, tier);
    }

    /** Whether the provider's API key is set; the models of a provider without a key fail on every call. */
    public static boolean available(ModelProvider provider) {
        return ConfigProvider.getConfig().getOptionalValue(provider.apiKeyVariable(), String.class)
                .filter(key -> !key.isBlank())
                .isPresent();
    }
}
