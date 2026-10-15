package dev.langchain4j.prreviewer.models;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.List;

/**
 * Keeps the agents stateless. Quarkus registers every agent as an AI service backed by the default chat memory
 * provider, and agents running outside a request context, the parallel and asynchronous ones, all use the memory id
 * "default": without this provider they would read each other's conversations. The memory it provides keeps nothing;
 * Quarkus still buffers the messages of one invocation, so tool loops see their own exchange.
 */
@ApplicationScoped
public class StatelessAgents {

    @Produces
    @ApplicationScoped
    ChatMemoryProvider noConversationMemory() {
        return NoMemory::new;
    }

    private record NoMemory(Object id) implements ChatMemory {

        @Override
        public void add(ChatMessage message) {}

        @Override
        public List<ChatMessage> messages() {
            return List.of();
        }

        @Override
        public void clear() {}
    }
}
