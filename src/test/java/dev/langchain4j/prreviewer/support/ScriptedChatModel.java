package dev.langchain4j.prreviewer.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.prreviewer.observability.AgentAttribution;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A chat model answering each agent with the scenario's canned response, chosen by the "## Task: name" line its
 * prompt starts with, or by the running agent's name when the scenario has one. A response may be a function of the
 * call, and a {@link RuntimeException} is thrown.
 * It declares structured output support like the Anthropic models, so the real prompt templates, response formats and
 * output parsers are all exercised.
 */
public final class ScriptedChatModel implements ChatModel {

    private static final Pattern TASK = Pattern.compile("## Task: (\\w+)");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** One captured call. */
    public record Call(String agent, String task, String modelName, String system, String user, ChatRequest request) {}

    private final String modelName;
    private final TestScenario scenario;
    private final List<ChatModelListener> listeners;

    public ScriptedChatModel(String modelName, TestScenario scenario, List<ChatModelListener> listeners) {
        this.modelName = modelName;
        this.scenario = scenario;
        this.listeners = listeners;
    }

    public static List<Call> newCallLog() {
        return Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChatResponse doChat(ChatRequest request) {
        String system = text(request.messages(), SystemMessage.class);
        String user = text(request.messages(), UserMessage.class);
        Matcher matcher = TASK.matcher(user);
        if (!matcher.find()) {
            throw new AssertionError("No task line in the prompt: " + user);
        }
        String task = matcher.group(1);
        Call call = new Call(AgentAttribution.current(), task, modelName, system, user, request);
        scenario.calls().add(call);
        if (scenario.failingTasks().contains(task)) {
            throw new IllegalStateException("Scripted failure of " + task);
        }
        // the agent's own response first: fallback reviewers share the prompt, so the task, of the main ones
        Object response = scenario.responses().getOrDefault(call.agent(), scenario.responses().get(task));
        if (response instanceof Function<?, ?> function) {
            response = ((Function<Call, Object>) function).apply(call);
        }
        if (response instanceof RuntimeException failure) {
            throw failure;
        }
        if (response == null) {
            throw new AssertionError("No scripted response for " + task);
        }
        String text;
        try {
            text = response instanceof String s ? s : JSON.writeValueAsString(wrapIfNeeded(response, request));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .modelName(modelName)
                .tokenUsage(new TokenUsage(1000, 100))
                .build();
    }

    /** Collections are returned wrapped in a "values" property when the requested schema says so. */
    private static Object wrapIfNeeded(Object response, ChatRequest request) {
        if (response instanceof List<?> list && request.responseFormat() != null
                && request.responseFormat().jsonSchema() != null
                && request.responseFormat().jsonSchema().rootElement() instanceof JsonObjectSchema root
                && root.properties().containsKey("values")) {
            return Map.of("values", list);
        }
        return response;
    }

    private static String text(List<ChatMessage> messages, Class<?> type) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage s && type == SystemMessage.class) {
                sb.append(s.text());
            } else if (message instanceof UserMessage u && type == UserMessage.class) {
                sb.append(u.singleText());
            }
        }
        return sb.toString();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }
}
