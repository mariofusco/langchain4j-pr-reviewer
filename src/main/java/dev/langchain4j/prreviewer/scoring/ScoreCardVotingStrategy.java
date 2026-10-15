package dev.langchain4j.prreviewer.scoring;

import dev.langchain4j.agentic.patterns.voting.VotingStrategy;
import dev.langchain4j.prreviewer.model.Metric;
import dev.langchain4j.prreviewer.model.MetricScore;
import dev.langchain4j.prreviewer.model.ScoreCard;
import dev.langchain4j.prreviewer.model.Verdict;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates the score cards of the voters: each metric is applicable when most voters say so and scores the mean
 * of the applicable votes; the verdict is the majority, ties going to the stricter one; the confidence is the lowest.
 */
public final class ScoreCardVotingStrategy {

    private ScoreCardVotingStrategy() {}

    public static VotingStrategy averageAndMajority() {
        return ScoreCardVotingStrategy::aggregate;
    }

    static ScoreCard aggregate(Collection<Object> votes) {
        List<ScoreCard> cards = votes.stream()
                .filter(ScoreCard.class::isInstance)
                .map(ScoreCard.class::cast)
                .toList();
        if (cards.isEmpty()) {
            return null;
        }

        List<MetricScore> metrics = new ArrayList<>();
        for (Metric metric : Metric.values()) {
            List<MetricScore> scores = cards.stream()
                    .map(card -> Caps.normalize(card.metrics()).get(metric))
                    .toList();
            List<MetricScore> applicable = scores.stream().filter(MetricScore::applicable).toList();
            if (applicable.size() * 2 <= scores.size() && !(applicable.size() * 2 == scores.size() && !applicable.isEmpty())) {
                metrics.add(new MetricScore(metric, false, 0.0, scores.get(0).rationale()));
                continue;
            }
            double mean = applicable.stream().mapToDouble(MetricScore::score).average().orElse(0.0);
            MetricScore closest = applicable.stream()
                    .min(Comparator.comparingDouble(m -> Math.abs(m.score() - mean)))
                    .orElseThrow();
            metrics.add(new MetricScore(metric, true, Caps.round(mean), closest.rationale()));
        }

        Map<Verdict, Integer> counts = new EnumMap<>(Verdict.class);
        cards.forEach(card -> counts.merge(card.proposedVerdict() == null ? Verdict.COMMENT : card.proposedVerdict(), 1,
                Integer::sum));
        int max = counts.values().stream().max(Integer::compare).orElse(0);
        Verdict verdict = counts.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .reduce(Verdict::stricter)
                .orElse(Verdict.COMMENT);
        String reasoning = cards.stream()
                .filter(card -> card.proposedVerdict() == verdict)
                .map(ScoreCard::verdictReasoning)
                .findFirst()
                .orElse("");
        double confidence = cards.stream().mapToDouble(ScoreCard::confidence).min().orElse(0.5);
        return new ScoreCard(List.copyOf(metrics), verdict,
                reasoning + " (" + cards.size() + " independent scorers, majority verdict)", confidence);
    }
}
