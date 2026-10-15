package dev.langchain4j.prreviewer.eval;

import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.Severity;
import dev.langchain4j.prreviewer.model.Verdict;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Metrics of the eval, pure functions over the labels and the results of the runs. */
public final class EvalMetrics {

    /** A must-find item is found by a finding on the same file within this many lines (or anywhere when unknown). */
    public static final int LINE_TOLERANCE = 5;

    /** The result of one review run of one case. */
    public record Run(Verdict verdict, double overall, Map<String, Double> scores, List<Finding> findings,
                      double costUsd, double seconds) {}

    private EvalMetrics() {}

    /** The verdict most runs gave, ties going to the stricter. */
    public static Verdict majorityVerdict(List<Run> runs) {
        Map<Verdict, Integer> counts = new EnumMap<>(Verdict.class);
        runs.forEach(r -> counts.merge(r.verdict(), 1, Integer::sum));
        int max = counts.values().stream().max(Integer::compare).orElse(0);
        return counts.entrySet().stream().filter(e -> e.getValue() == max).map(Map.Entry::getKey)
                .reduce(Verdict::stricter).orElse(null);
    }

    public static boolean found(EvalCase.MustFind item, List<Finding> findings) {
        return findings.stream().anyMatch(f -> sameFile(f.file(), item.file())
                && (item.line() <= 0 || f.line() <= 0 || Math.abs(f.line() - item.line()) <= LINE_TOLERANCE));
    }

    /** Share of the must-find items raised by a run, or null when the case has none. */
    public static Double recall(EvalCase c, Run run) {
        if (c.mustFind().isEmpty()) {
            return null;
        }
        long found = c.mustFind().stream().filter(item -> found(item, run.findings())).count();
        return (double) found / c.mustFind().size();
    }

    /**
     * Share of the run's findings of at least MEDIUM severity that match no must-find item, or null when there are
     * none. An upper bound of false positives: maintainers do not comment on every real issue.
     */
    public static Double unmatchedRate(EvalCase c, Run run) {
        List<Finding> significant = run.findings().stream()
                .filter(f -> f.active() && f.severity().atLeast(Severity.MEDIUM)).toList();
        if (significant.isEmpty()) {
            return null;
        }
        long unmatched = significant.stream()
                .filter(f -> c.mustFind().stream().noneMatch(item -> found(item, List.of(f))))
                .count();
        return (double) unmatched / significant.size();
    }

    public static double standardDeviation(List<Double> values) {
        if (values.size() < 2) {
            return 0.0;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / (values.size() - 1);
        return Math.sqrt(variance);
    }

    public static Double mean(List<Double> values) {
        List<Double> present = values.stream().filter(v -> v != null).toList();
        return present.isEmpty() ? null : present.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static boolean sameFile(String a, String b) {
        return a.equals(b) || a.endsWith("/" + b) || b.endsWith("/" + a);
    }
}
