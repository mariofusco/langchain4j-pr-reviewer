package dev.langchain4j.prreviewer.models;

import java.util.List;
import java.util.Set;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.jitllm.JitLLMChatModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.prreviewer.observability.AgentAttribution;
import dev.langchain4j.prreviewer.observability.LocalInference;

/**
 * The jitllm model as the agents see it. The adapter leaves the model name out of its responses and has no listeners of
 * its own here, so this decorator names the model (the cost ledger and the progress rows key on it), runs the
 * application's listeners around each call with that name, and measures each call for the Local GPU panel.
 */
final class LocalChatModel implements ChatModel, AutoCloseable {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(LocalChatModel.class);

    /** One GPU: the calls of every local model run one at a time, so that queueing and generation are timed apart. */
    private static final Object GPU = new Object();

    private final JitLLMChatModel delegate;
    private final String modelName;
    private final List<ChatModelListener> listeners;
    private final LocalInference localInference;
    private boolean executionRead;

    LocalChatModel(JitLLMChatModel delegate, String modelName, List<ChatModelListener> listeners,
                   LocalInference localInference) {
        this.delegate = delegate;
        this.modelName = modelName;
        this.listeners = listeners;
        this.localInference = localInference;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        String agent = AgentAttribution.current();
        long queued = System.nanoTime();
        ChatResponse response;
        long started;
        synchronized (GPU) {
            started = System.nanoTime();
            response = delegate.doChat(request);
            if (!executionRead) {
                executionRead = true;
                localInference.executionKnown(modelName, LocalInference.describe(executionInfo()));
            }
        }
        long finished = System.nanoTime();
        TokenUsage usage = response.tokenUsage();
        localInference.call(modelName, agent,
                usage == null || usage.inputTokenCount() == null ? 0 : usage.inputTokenCount(),
                usage == null || usage.outputTokenCount() == null ? 0 : usage.outputTokenCount(),
                (started - queued) / 1_000_000, (finished - started) / 1_000_000);
        AiMessage message = response.aiMessage();
        if (LOG.isDebugEnabled()) {
            String prompt = request.messages().isEmpty() ? "" : request.messages().getLast().toString();
            LOG.debugf("%s: prompt ends %s%n answer: %s", agent,
                    prompt.substring(Math.max(0, prompt.length() - 600)), message == null ? null : message.text());
        }
        if (message != null && message.text() != null && !message.hasToolExecutionRequests()) {
            String repaired = LocalOutputRepair.repair(message.text(), listExpected(request));
            if (!repaired.equals(message.text())) {
                message = message.toBuilder().text(repaired).build();
            }
        }
        // the name goes into the metadata: a response cannot carry both metadata and a separate model name
        return ChatResponse.builder()
                .aiMessage(message)
                .metadata(response.metadata().toBuilder().modelName(modelName).build())
                .build();
    }

    /**
     * Whether the answer is meant to be a list. LangChain4j appends a JSON format to the user message of every agent
     * that returns a record, and none for a list of records when the model has no JSON-schema support; an agent that
     * returns text gets none either, but answers in prose, which the repair leaves alone.
     */
    private static boolean listExpected(ChatRequest request) {
        return request.messages().stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> ((UserMessage) m).hasSingleText() ? ((UserMessage) m).singleText() : "")
                .reduce((first, last) -> last)
                .map(text -> !text.contains("You must answer strictly in the following"))
                .orElse(false);
    }

    /**
     * jitllm's {@code ExecutionInfo} for the adapter's session (backend, device, TornadoVM version, prefill kernels,
     * native libraries), or null. The adapter keeps its session private, and preparing an already prepared session
     * returns its settings without moving it, so it is read once, by reflection, after the first call.
     */
    private Object executionInfo() {
        try {
            Class<?> base = delegate.getClass().getSuperclass();
            var field = base.getDeclaredField("session");
            field.setAccessible(true);
            Object session = field.get(delegate);
            if (session == null) {
                return null;
            }
            // through the public interface: the session's own class is not public
            return Class.forName("org.beehive.jitllm.api.GenerationSession", false, base.getClassLoader())
                    .getMethod("prepare").invoke(session);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OTHER;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Set.of();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
