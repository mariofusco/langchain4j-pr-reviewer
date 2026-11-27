package dev.langchain4j.prreviewer.web;

import java.util.List;
import java.util.Map;

import dev.langchain4j.prreviewer.observability.LocalInference;

/** Test access to the package-private views. */
public final class ViewsAccess {

    private ViewsAccess() {}

    public static Map<String, String> gpu(List<LocalInference.Call> calls) {
        Views.GpuView view = Views.GpuView.of(List.of(), calls);
        return Map.of("calls", String.valueOf(view.calls()), "outputTokens", String.valueOf(view.outputTokens()),
                "average", view.averageTokensPerSecond(), "peak", view.peakTokensPerSecond(),
                "queued", view.queuedSeconds());
    }
}
