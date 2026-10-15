package dev.langchain4j.prreviewer.model;

import java.util.List;
import java.util.Locale;

/** Calibrated scores. Its {@code toString()} is the rendering used in prompts. */
public record Scores(List<MetricScore> metrics, double overall, double confidence) {

    /** The score of the metric, or null when it is not applicable. */
    public Double score(Metric metric) {
        return metrics.stream()
                .filter(m -> m.metric() == metric && m.applicable())
                .map(MetricScore::score)
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (MetricScore m : metrics) {
            sb.append("\n- ").append(m.metric().label()).append(": ")
                    .append(m.applicable() ? String.format(Locale.ROOT, "%.1f", m.score()) : "n/a")
                    .append(" - ").append(m.rationale());
        }
        sb.append(String.format(Locale.ROOT, "\n- Overall: %.1f (confidence %.2f)", overall, confidence));
        return sb.toString();
    }
}
