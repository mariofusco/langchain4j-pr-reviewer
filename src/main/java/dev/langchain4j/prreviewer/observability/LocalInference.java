package dev.langchain4j.prreviewer.observability;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.langchain4j.prreviewer.app.ActiveReview;
import dev.langchain4j.prreviewer.app.RunEvent;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * What the local inference engine is and what it has done: the loaded model, the device and the execution path jitllm
 * chose, and every model call with its tokens and timings. The web interface shows it as the Local GPU panel, the CLI
 * as one line per call; both learn about a call through a {@link RunEvent.Kind#MODEL_CALL} event.
 */
@ApplicationScoped
@Unremovable
public class LocalInference {

    /** One model call: the agent, its tokens, the time it queued behind other agents and the time it ran. */
    public record Call(String model, String agent, long inputTokens, long outputTokens, long queuedMillis, long millis) {

        /** Generated tokens per second over the whole call, prompt processing included. */
        public double tokensPerSecond() {
            return millis <= 0 ? 0.0 : outputTokens * 1000.0 / millis;
        }
    }

    /** The engine: model file, size, quantization, context, and jitllm's execution settings once known. */
    public record Engine(String model, String file, String quantization, double sizeGiB, int contextLength,
                         boolean gpu, long loadMillis, Map<String, String> execution) {

        public String execution(String key) {
            return execution.getOrDefault(key, "");
        }

        /** The file size in GiB, one decimal. */
        public String size() {
            return String.format(Locale.ROOT, "%.1f", sizeGiB);
        }
    }

    private static final Pattern QUANTIZATION =
            Pattern.compile("(?i)[-.](UD-)?((?:I?Q\\d(?:_[A-Z0-9]+)*)|F16|BF16|F32)(?=\\.gguf$)");

    @Inject
    ActiveReview activeReview;

    private final Map<String, Engine> engines = new LinkedHashMap<>();
    private final List<Call> calls = new ArrayList<>();

    /** The model name the ledger and the progress rows show: {@code jitllm/} and the file name without extension. */
    public static String modelName(Path path) {
        String file = path.getFileName().toString();
        return "jitllm/" + (file.endsWith(".gguf") ? file.substring(0, file.length() - 5) : file);
    }

    public synchronized void engineLoaded(String model, Path path, int contextLength, boolean gpu, long loadMillis) {
        String file = path.getFileName().toString();
        Matcher m = QUANTIZATION.matcher(file);
        String quantization = m.find() ? m.group(2).toUpperCase(Locale.ROOT) : "";
        double size;
        try {
            size = Files.size(path) / (1024.0 * 1024 * 1024);
        } catch (IOException e) {
            size = 0;
        }
        engines.put(model, new Engine(model, file, quantization, size, contextLength, gpu, loadMillis, Map.of()));
    }

    /** jitllm's execution settings ({@code ExecutionInfo}) of a model, read once its first session is prepared. */
    public synchronized void executionKnown(String model, Map<String, String> execution) {
        Engine e = engines.get(model);
        if (e != null) {
            engines.put(model, new Engine(e.model(), e.file(), e.quantization(), e.sizeGiB(), e.contextLength(),
                    e.gpu(), e.loadMillis(), Map.copyOf(execution)));
        }
    }

    public void call(String model, String agent, long inputTokens, long outputTokens, long queuedMillis, long millis) {
        Call call = new Call(model, agent, inputTokens, outputTokens, queuedMillis, millis);
        synchronized (this) {
            calls.add(call);
        }
        activeReview.publish(new RunEvent(RunEvent.Kind.MODEL_CALL, agent, model, millis, activeReview.ledger().total(),
                String.format(Locale.ROOT, "%d in · %d out · %.1f tok/s", inputTokens, outputTokens,
                        call.tokensPerSecond())));
    }

    public static LocalInference get() {
        return io.quarkus.arc.Arc.container().instance(LocalInference.class).get();
    }

    /** The last call, or null: calls run one at a time, so it is the one a {@code MODEL_CALL} event announces. */
    public synchronized Call latest() {
        return calls.isEmpty() ? null : calls.get(calls.size() - 1);
    }

    /** The loaded models, in load order. */
    public synchronized List<Engine> engines() {
        return List.copyOf(engines.values());
    }

    public synchronized List<Call> calls() {
        return List.copyOf(calls);
    }

    /** The calls from the given index on: a review reads the calls made since it started. */
    public synchronized List<Call> callsSince(int from) {
        return List.copyOf(calls.subList(Math.min(Math.max(0, from), calls.size()), calls.size()));
    }

    public synchronized int callCount() {
        return calls.size();
    }

    /** Reads the public accessors of a record (jitllm's {@code ExecutionInfo}) as strings, in declaration order. */
    public static Map<String, String> describe(Object record) {
        Map<String, String> values = new LinkedHashMap<>();
        if (record != null && record.getClass().isRecord()) {
            for (var component : record.getClass().getRecordComponents()) {
                try {
                    values.put(component.getName(), String.valueOf(component.getAccessor().invoke(record)));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // an accessor that cannot be read is left out
                }
            }
        }
        return values;
    }
}
