package dev.langchain4j.prreviewer.scoring;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.DiffStats;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.ScoreCard;
import dev.langchain4j.prreviewer.model.Scores;
import dev.langchain4j.prreviewer.model.Verdict;
import dev.langchain4j.prreviewer.model.Verification;
import java.util.List;

/** Non-AI agent: caps the proposed scores and raises the proposed verdict to the floor the findings require. */
public class ScoreCalibrator {

    /** Confidence ceiling when the review is known to be partial. */
    public static final double PARTIAL_REVIEW_CONFIDENCE = 0.6;

    @Agent(name = "scoreCalibrator", description = "Applies the score caps and the verdict floor",
            typedOutputKey = Keys.Scores.class)
    public static Scores calibrate(AgenticScope scope,
                                   @K(Keys.ScoreCard.class) ScoreCard card,
                                   @K(Keys.ActiveFindings.class) List<Finding> findings,
                                   @K(Keys.DiffStats.class) DiffStats stats,
                                   @K(Keys.PrSummary.class) PrSummary summary) {
        Caps.Result capped = Caps.apply(card.metrics(), findings, stats, summary);
        VerdictRules.Floor floor = VerdictRules.floor(findings);
        Verdict proposed = card.proposedVerdict() == null ? Verdict.COMMENT : card.proposedVerdict();
        Verdict verdict = VerdictRules.apply(proposed, floor);

        double confidence = Math.clamp(card.confidence(), 0.0, 1.0);
        if (partialReview(scope, stats, findings)) {
            confidence = Math.min(confidence, PARTIAL_REVIEW_CONFIDENCE);
        }

        String reasoning = VerdictRules.explain(card.verdictReasoning(), proposed, floor, verdict);
        if (!capped.appliedRules().isEmpty()) {
            reasoning = reasoning + " Score caps: " + String.join("; ", capped.appliedRules()) + ".";
        }
        scope.writeState(Keys.Verdict.class, verdict);
        scope.writeState(Keys.VerdictReasoning.class, reasoning.strip());
        return new Scores(capped.metrics(), Caps.overall(capped.metrics()), Caps.round(confidence * 10) / 10.0);
    }

    private static boolean partialReview(AgenticScope scope, DiffStats stats, List<Finding> findings) {
        return stats.truncated()
                || !scope.readState(Keys.DegradedAgents.class).isEmpty()
                || findings.stream().anyMatch(f -> f.verification() == Verification.UNSURE);
    }
}
