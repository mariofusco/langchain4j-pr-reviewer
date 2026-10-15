package dev.langchain4j.prreviewer.model;

/**
 * The score of one metric.
 *
 * @param applicable false when the metric does not apply to this pull request (its score is then ignored)
 * @param score      from 0.0 to 10.0
 */
public record MetricScore(Metric metric, boolean applicable, double score, String rationale) {

    public MetricScore withScore(double newScore, String newRationale) {
        return new MetricScore(metric, applicable, newScore, newRationale);
    }
}
