package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.prreviewer.app.ActiveReview;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.DiffStats;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.ScoreCard;
import dev.langchain4j.prreviewer.scoring.VotingTrigger;
import java.util.List;

/** Asks three independent scorers only when the draft score card is borderline, and only within the cost cap. */
public interface VotingGate {

    @ConditionalAgent(name = "votingGate", description = "Votes on borderline score cards",
            outputKey = Keys.SCORE_CARD, subAgents = ScoreVoting.class)
    ScoreCard vote(ScoreCard scoreCard);

    @ActivationCondition(value = ScoreVoting.class, description = "borderline score card within the cost cap")
    static boolean borderline(ScoreCard scoreCard,
                              List<Finding> activeFindings,
                              DiffStats diffStats,
                              PrSummary prSummary,
                              ReviewOptions options) {
        if (!VotingTrigger.isBorderline(scoreCard, activeFindings, diffStats, prSummary)) {
            return false;
        }
        if (ActiveReview.get().ledger().exceeded(options.maxCostUsd())) {
            ActiveReview.get().ledger().noteCapSkip("score voting on a borderline review");
            return false;
        }
        return true;
    }
}
