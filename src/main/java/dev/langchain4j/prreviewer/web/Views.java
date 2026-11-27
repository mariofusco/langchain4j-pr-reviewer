package dev.langchain4j.prreviewer.web;

import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.observability.LocalInference;
import java.util.Locale;
import java.util.List;

/** What the pages show. */
final class Views {

    private Views() {}

    /** A choice of a select box. */
    record Choice(String value, String label, boolean selected) {}

    record IndexPage(String pr, String error, List<Choice> modes, List<Choice> providers, List<Choice> decisionModels,
                     List<Choice> tiers, ReviewOptions defaults, String routerThreshold, List<WebRun> runs,
                     List<ReportHistory.Entry> history) {}

    record RunPage(WebRun run, InteractionView interaction) {}

    /**
     * The Local GPU panel: the engine jitllm runs (model, device, execution path) and what this review asked of it,
     * with a sparkline of the generation speed of every call.
     */
    record GpuView(List<LocalInference.Engine> engines, LocalInference.Engine device, List<CallRow> recent, int calls, long inputTokens, long outputTokens,
                   String gpuSeconds, String queuedSeconds, String averageTokensPerSecond, String peakTokensPerSecond,
                   String sparkline, String sparkArea) {

        static final int WIDTH = 320;
        static final int HEIGHT = 56;

        /** One call as the table shows it. */
        record CallRow(String agent, String model, long inputTokens, long outputTokens, String seconds,
                       String tokensPerSecond) {}

        /**
         * @param engines the loaded models; the first whose execution settings are known describes the device
         */
        static GpuView of(List<LocalInference.Engine> engines, List<LocalInference.Call> calls) {
            LocalInference.Engine device = engines.stream().filter(e -> !e.execution().isEmpty()).findFirst()
                    .orElse(engines.isEmpty() ? null : engines.getFirst());
            long input = 0;
            long output = 0;
            long millis = 0;
            long queued = 0;
            double peak = 0;
            for (LocalInference.Call c : calls) {
                input += c.inputTokens();
                output += c.outputTokens();
                millis += c.millis();
                queued += c.queuedMillis();
                peak = Math.max(peak, c.tokensPerSecond());
            }
            double average = millis == 0 ? 0 : output * 1000.0 / millis;
            List<CallRow> recent = calls.subList(Math.max(0, calls.size() - 8), calls.size()).reversed().stream()
                    .map(c -> new CallRow(c.agent(), c.model().replaceFirst("^jitllm/", ""), c.inputTokens(),
                            c.outputTokens(),
                            String.format(Locale.ROOT, "%.1f", c.millis() / 1000.0),
                            String.format(Locale.ROOT, "%.1f", c.tokensPerSecond())))
                    .toList();
            // the sparkline: one point per call, scaled to the fastest call
            StringBuilder line = new StringBuilder();
            double top = Math.max(peak, 1.0);
            int n = calls.size();
            for (int i = 0; i < n; i++) {
                double x = n == 1 ? WIDTH / 2.0 : 4 + i * (WIDTH - 8.0) / (n - 1);
                double y = HEIGHT - 4 - calls.get(i).tokensPerSecond() / top * (HEIGHT - 10);
                line.append(i == 0 ? "" : " ").append(String.format(Locale.ROOT, "%.1f,%.1f", x, y));
            }
            String area = n < 2 ? "" : String.format(Locale.ROOT, "4,%d %s %.1f,%d", HEIGHT, line, WIDTH - 4.0, HEIGHT);
            return new GpuView(List.copyOf(engines), device, recent, n, input, output, String.format(Locale.ROOT, "%.0f", millis / 1000.0),
                    String.format(Locale.ROOT, "%.0f", queued / 1000.0), String.format(Locale.ROOT, "%.1f", average),
                    String.format(Locale.ROOT, "%.1f", peak), line.toString(), area);
        }
    }

    /** A finding the reviewer can contest or drop. */
    record FindingRow(String id, String severity, String category, String title, String location, String verification) {

        static FindingRow of(Finding f) {
            return new FindingRow(f.id(), f.severity().name(), f.category().name(), f.title(), f.location(),
                    f.verification().name());
        }
    }

    /**
     * What the run waits for: {@code kind} is {@code none}, {@code clarification} or {@code approval}.
     */
    record InteractionView(String runId, String kind, List<String> questions, String reportHtml,
                           List<FindingRow> findings, String spent, String cap, boolean debatesAffordable, String error) {

        static InteractionView none(String runId) {
            return new InteractionView(runId, "none", List.of(), "", List.of(), "", "", true, null);
        }
    }

    record ReportPage(String directory, String pr, String verdict, String overall, String confidence, String cost,
                      String html, boolean hasTrace) {}
}
