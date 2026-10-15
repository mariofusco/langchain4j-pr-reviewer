package dev.langchain4j.prreviewer.models;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import java.util.List;

/** The provider of the chat models behind the three tiers. */
public enum ChatModelProvider {
    ANTHROPIC,
    OPENAI;

    /** The models of this provider, configured from the environment ({@code ANTHROPIC_API_KEY} or {@code OPENAI_API_KEY}). */
    public ChatModels fromEnvironment(List<ChatModelListener> listeners) {
        return switch (this) {
            case ANTHROPIC -> AnthropicModels.fromEnvironment(listeners);
            case OPENAI -> OpenAiModels.fromEnvironment(listeners);
        };
    }
}
