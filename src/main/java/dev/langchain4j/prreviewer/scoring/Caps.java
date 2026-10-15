package dev.langchain4j.prreviewer.scoring;

import dev.langchain4j.prreviewer.model.Category;
import dev.langchain4j.prreviewer.model.DiffStats;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.FindingCategory;
import dev.langchain4j.prreviewer.model.Metric;
import dev.langchain4j.prreviewer.model.MetricScore;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.Severity;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic caps on the proposed scores. Caps only ever lower a score: the model owns the judgment, the rules own
 * the floor.
 */
public final class Caps {

    /** The capped scores and the rules that changed something. */
    public record Result(List<MetricScore> metrics, List<String> appliedRules) {}

    private Caps() {}

    public static Result apply(List<MetricScore> proposed, List<Finding> findings, DiffStats stats, PrSummary summary) {
        Map<Metric, MetricScore> byMetric = normalize(proposed);
        List<String> applied = new ArrayList<>();

        Findings.worst(findings, FindingCategory.SECURITY, Severity.HIGH).ifPresent(f ->
                cap(byMetric, Metric.SECURITY, f.severity() == Severity.CRITICAL ? 2.0 : 5.0,
                        f.severity() + " security finding " + f.id(), applied));
        Findings.worst(findings, FindingCategory.CORRECTNESS, Severity.HIGH).ifPresent(f ->
                cap(byMetric, Metric.CORRECTNESS, f.severity() == Severity.CRITICAL ? 3.0 : 5.0,
                        f.severity() + " correctness finding " + f.id(), applied));
        if (summary != null && stats != null && !stats.hasTests()
                && (summary.category() == Category.BUGFIX || summary.category() == Category.FEATURE)) {
            cap(byMetric, Metric.TEST_COVERAGE, 4.0, summary.category() + " without test changes", applied);
        }

        List<MetricScore> result = new ArrayList<>();
        for (Metric metric : Metric.values()) {
            MetricScore m = byMetric.get(metric);
            result.add(m.withScore(round(Math.clamp(m.score(), 0.0, 10.0)), m.rationale()));
        }
        return new Result(List.copyOf(result), List.copyOf(applied));
    }

    /** Weighted mean of the applicable metrics, weights renormalized; 0 when nothing is applicable. */
    public static double overall(List<MetricScore> metrics) {
        double weighted = 0;
        double weights = 0;
        for (MetricScore m : metrics) {
            if (m.applicable() && m.metric().weight() > 0) {
                weighted += m.score() * m.metric().weight();
                weights += m.metric().weight();
            }
        }
        return weights == 0 ? 0.0 : round(weighted / weights);
    }

    /** One entry per metric; metrics the model left out are marked not applicable. */
    static Map<Metric, MetricScore> normalize(List<MetricScore> proposed) {
        Map<Metric, MetricScore> byMetric = new EnumMap<>(Metric.class);
        if (proposed != null) {
            for (MetricScore m : proposed) {
                if (m != null && m.metric() != null) {
                    byMetric.putIfAbsent(m.metric(), m);
                }
            }
        }
        for (Metric metric : Metric.values()) {
            byMetric.putIfAbsent(metric, new MetricScore(metric, false, 0.0, "not scored"));
        }
        return byMetric;
    }

    private static void cap(Map<Metric, MetricScore> byMetric, Metric metric, double max, String reason,
                            List<String> applied) {
        MetricScore current = byMetric.get(metric);
        if (!current.applicable()) {
            // a finding in this area makes the metric applicable
            current = new MetricScore(metric, true, max, current.rationale());
        }
        if (current.score() > max) {
            byMetric.put(metric, current.withScore(max, current.rationale()
                    + String.format(Locale.ROOT, " (capped at %.1f: %s)", max, reason)));
            applied.add(String.format(Locale.ROOT, "%s capped at %.1f because of %s", metric.label(), max, reason));
        } else {
            byMetric.put(metric, current);
        }
    }

    static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
