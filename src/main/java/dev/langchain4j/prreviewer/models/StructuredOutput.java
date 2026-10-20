package dev.langchain4j.prreviewer.models;

import dev.langchain4j.model.anthropic.AnthropicChatModel.AnthropicChatModelBuilder;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Native structured output for agents returning records and lists: every named chat model declares the JSON schema
 * capability, without a default response format, so agents returning text stay unaffected. The configuration offers
 * the capability only together with a JSON response format, and customizers apply only to the model their
 * {@link ModelName} names, hence one producer per named model.
 */
@ApplicationScoped
public class StructuredOutput {

    @Produces
    @ModelName("anthropic-economy")
    ModelBuilderCustomizer<AnthropicChatModelBuilder> anthropicEconomy() {
        return anthropic();
    }

    @Produces
    @ModelName("anthropic-standard")
    ModelBuilderCustomizer<AnthropicChatModelBuilder> anthropicStandard() {
        return anthropic();
    }

    @Produces
    @ModelName("anthropic-premium")
    ModelBuilderCustomizer<AnthropicChatModelBuilder> anthropicPremium() {
        return anthropic();
    }

    @Produces
    @ModelName("openai-economy")
    ModelBuilderCustomizer<OpenAiChatModelBuilder> openAiEconomy() {
        return openAi();
    }

    @Produces
    @ModelName("openai-standard")
    ModelBuilderCustomizer<OpenAiChatModelBuilder> openAiStandard() {
        return openAi();
    }

    @Produces
    @ModelName("openai-premium")
    ModelBuilderCustomizer<OpenAiChatModelBuilder> openAiPremium() {
        return openAi();
    }

    @Produces
    @ModelName("openai-economy-tools")
    ModelBuilderCustomizer<OpenAiChatModelBuilder> openAiEconomyTools() {
        return openAi();
    }

    @Produces
    @ModelName("openai-standard-tools")
    ModelBuilderCustomizer<OpenAiChatModelBuilder> openAiStandardTools() {
        return openAi();
    }

    @Produces
    @ModelName("openai-premium-tools")
    ModelBuilderCustomizer<OpenAiChatModelBuilder> openAiPremiumTools() {
        return openAi();
    }

    private static ModelBuilderCustomizer<AnthropicChatModelBuilder> anthropic() {
        return builder -> builder.supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
    }

    private static ModelBuilderCustomizer<OpenAiChatModelBuilder> openAi() {
        return builder -> builder.supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
    }
}
