package dev.langchain4j.prreviewer.models;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.model.ModelTier;

/** The chat model of each tier. Production uses {@link AnthropicModels} or {@link OpenAiModels}; tests install scripted models. */
@FunctionalInterface
public interface ChatModels {

    ChatModel model(ModelTier tier);

    /** The model of the tier for agents that call tools; the same model unless the provider needs a variant. */
    default ChatModel toolModel(ModelTier tier) {
        return model(tier);
    }
}
