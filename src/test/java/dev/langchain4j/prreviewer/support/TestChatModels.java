package dev.langchain4j.prreviewer.support;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.observability.CostTrackingListener;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.List;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Replaces every named chat model with a scripted double answering from the {@link TestScenario}. The provider beans
 * are default beans, so these producers take precedence; the model names come from the real configuration, so the
 * tests also check it. Excluded by the profiles of the integration tests that use real models.
 */
@ApplicationScoped
public class TestChatModels {

    @Inject
    TestScenario scenario;

    @Inject
    CostTrackingListener costs;

    @Produces
    @ApplicationScoped
    @ModelName("anthropic-economy")
    ChatModel anthropicEconomy() {
        return model("anthropic", "anthropic-economy");
    }

    @Produces
    @ApplicationScoped
    @ModelName("anthropic-standard")
    ChatModel anthropicStandard() {
        return model("anthropic", "anthropic-standard");
    }

    @Produces
    @ApplicationScoped
    @ModelName("anthropic-premium")
    ChatModel anthropicPremium() {
        return model("anthropic", "anthropic-premium");
    }

    @Produces
    @ApplicationScoped
    @ModelName("openai-economy")
    ChatModel openAiEconomy() {
        return model("openai", "openai-economy");
    }

    @Produces
    @ApplicationScoped
    @ModelName("openai-standard")
    ChatModel openAiStandard() {
        return model("openai", "openai-standard");
    }

    @Produces
    @ApplicationScoped
    @ModelName("openai-premium")
    ChatModel openAiPremium() {
        return model("openai", "openai-premium");
    }

    @Produces
    @ApplicationScoped
    @ModelName("openai-standard-tools")
    ChatModel openAiStandardTools() {
        return model("openai", "openai-standard-tools");
    }

    @Produces
    @ApplicationScoped
    @ModelName("openai-premium-tools")
    ChatModel openAiPremiumTools() {
        return model("openai", "openai-premium-tools");
    }

    /** The model name a named configuration uses, for instance {@code claude-haiku-5-5}. */
    public static String modelName(String provider, String configName) {
        return ConfigProvider.getConfig().getValue(
                "quarkus.langchain4j." + provider + "." + configName + ".chat-model.model-name", String.class);
    }

    private ChatModel model(String provider, String configName) {
        return new ScriptedChatModel(modelName(provider, configName), scenario, List.of(costs));
    }

}
