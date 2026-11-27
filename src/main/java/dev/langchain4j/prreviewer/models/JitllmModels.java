package dev.langchain4j.prreviewer.models;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.jitllm.JitLLMChatModel;
import dev.langchain4j.prreviewer.config.ReviewerConfig;
import dev.langchain4j.prreviewer.observability.LocalInference;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * The chat models of the {@code jitllm} provider: GGUF models loaded in this process on the GPU that the TornadoVM SDK
 * provides (Metal on Apple silicon, CUDA or OpenCL elsewhere), served under the three tier names.
 *
 * <p>{@code reviewer.jitllm.model-path} serves every tier; {@code economy-model-path}, {@code standard-model-path} and
 * {@code premium-model-path} give a tier its own model, as the hosted providers do with Haiku, Sonnet and Opus. On a
 * laptop GPU a small model for the routine work and a large one for the escalations keeps a review within minutes:
 * the reviewer escalates to the premium tier only on evidence (high risk, security-sensitive code, {@code --deep}, a
 * critical contested finding). Each distinct file is loaded once, on the first call of a tier that uses it, so another
 * provider never loads a model. All local calls take one GPU lock ({@link LocalChatModel}): the parallel reviewers
 * queue.
 *
 * <p>Structured output: jitllm rejects a JSON response format, so these models do not declare
 * {@code RESPONSE_FORMAT_JSON_SCHEMA} (see {@link StructuredOutput}) and LangChain4j puts the format in the prompt.
 *
 * <p>The JVM has to be started through TornadoVM ({@code @$TORNADOVM_HOME/tornado-argfile}); {@code bin/pr-reviewer-local}
 * does it.
 */
@ApplicationScoped
public class JitllmModels {

    static final String MODEL_PATH = "reviewer.jitllm.model-path";

    @Inject
    ReviewerConfig config;

    @Inject
    @Any
    Instance<ChatModelListener> listeners;

    @Inject
    LocalInference localInference;

    /** One model per file and thinking mode: thinking is a property of jitllm's chat model, set when it is built. */
    private record Key(Path path, boolean think) {}

    private final Map<Key, LocalChatModel> models = new LinkedHashMap<>();

    /** Whether a model file is configured and present: the provider is offered only then. */
    public static boolean configured() {
        return ConfigProvider.getConfig().getOptionalValue(MODEL_PATH, Path.class)
                .filter(Files::isRegularFile)
                .isPresent();
    }

    @Produces
    @ApplicationScoped
    @ModelName("jitllm-economy")
    ChatModel economy() {
        return model(config.jitllm().economyModelPath(), config.jitllm().think());
    }

    @Produces
    @ApplicationScoped
    @ModelName("jitllm-standard")
    ChatModel standard() {
        return model(config.jitllm().standardModelPath(), config.jitllm().think());
    }

    @Produces
    @ApplicationScoped
    @ModelName("jitllm-premium")
    ChatModel premium() {
        return model(config.jitllm().premiumModelPath(),
                config.jitllm().premiumThink().orElse(config.jitllm().think()));
    }

    /**
     * A tier's model. The beans are application-scoped client proxies, so this runs, and the file loads, on the first
     * call of the tier. A tier without its own file, or with a missing one, takes the default model.
     */
    private ChatModel model(Optional<Path> tierPath, boolean think) {
        return load(think, tierPath.filter(Files::isRegularFile)
                .or(() -> config.jitllm().modelPath().filter(Files::isRegularFile))
                .orElseThrow(() -> new IllegalStateException(
                        "The jitllm provider needs a GGUF model: set " + MODEL_PATH + " (or JITLLM_MODEL)")));
    }

    private synchronized LocalChatModel load(boolean think, Path path) {
        return models.computeIfAbsent(new Key(path.toAbsolutePath().normalize(), think), key -> {
            Path p = key.path();
            ReviewerConfig.Jitllm settings = config.jitllm();
            long start = System.nanoTime();
            JitLLMChatModel jitllm = JitLLMChatModel.builder()
                    .modelPath(p)
                    .contextLength(settings.contextLength())
                    .onGPU(settings.gpu())
                    .temperature(settings.temperature())
                    .topP(settings.topP())
                    .maxTokens(settings.maxTokens())
                    .think(think)
                    .returnThinking(false)
                    .build();
            long loadMillis = (System.nanoTime() - start) / 1_000_000;
            String name = LocalInference.modelName(p) + (think ? "" : " (no thinking)");
            localInference.engineLoaded(name, p, settings.contextLength(), settings.gpu(), loadMillis);
            return new LocalChatModel(jitllm, name, List.copyOf(listeners.stream().toList()), localInference);
        });
    }

    @PreDestroy
    synchronized void close() {
        models.values().forEach(LocalChatModel::close);
        models.clear();
    }
}
