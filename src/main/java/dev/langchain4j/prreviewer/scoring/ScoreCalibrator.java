package dev.langchain4j.prreviewer.scoring;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
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
            outputKey = Keys.SCORES)
    public static Scores calibrate(AgenticScope scope,
                                   ScoreCard scoreCard,
                                   List<Finding> activeFindings,
                                   DiffStats diffStats,
                                   PrSummary prSummary) {
        Caps.Result capped = Caps.apply(scoreCard.metrics(), activeFindings, diffStats, prSummary);
        VerdictRules.Floor floor = VerdictRules.floor(activeFindings);
        Verdict proposed = scoreCard.proposedVerdict() == null ? Verdict.COMMENT : scoreCard.proposedVerdict();
        Verdict verdict = VerdictRules.apply(proposed, floor);

        double confidence = Math.clamp(scoreCard.confidence(), 0.0, 1.0);
        if (partialReview(scope, diffStats, activeFindings)) {
            confidence = Math.min(confidence, PARTIAL_REVIEW_CONFIDENCE);
        }

        String reasoning = VerdictRules.explain(scoreCard.verdictReasoning(), proposed, floor, verdict);
        if (!capped.appliedRules().isEmpty()) {
            reasoning = reasoning + " Score caps: " + String.join("; ", capped.appliedRules()) + ".";
        }
        scope.writeState(Keys.VERDICT, verdict);
        scope.writeState(Keys.VERDICT_REASONING, reasoning.strip());
        return new Scores(capped.metrics(), Caps.overall(capped.metrics()), Caps.round(confidence * 10) / 10.0);
    }

    private static boolean partialReview(AgenticScope scope, DiffStats stats, List<Finding> findings) {
        return stats.truncated()
                || !ScopeState.<List<String>>read(scope, Keys.DEGRADED_AGENTS).isEmpty()
                || findings.stream().anyMatch(f -> f.verification() == Verification.UNSURE);
    }
}
