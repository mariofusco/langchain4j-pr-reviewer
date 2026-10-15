package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.prreviewer.AppContext;
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
            typedOutputKey = Keys.ScoreCard.class, subAgents = ScoreVoting.class)
    ScoreCard vote(@K(Keys.ScoreCard.class) ScoreCard draft);

    @ActivationCondition(value = ScoreVoting.class, description = "borderline score card within the cost cap")
    static boolean borderline(@K(Keys.ScoreCard.class) ScoreCard draft,
                              @K(Keys.ActiveFindings.class) List<Finding> findings,
                              @K(Keys.DiffStats.class) DiffStats stats,
                              @K(Keys.PrSummary.class) PrSummary summary,
                              @K(Keys.Options.class) ReviewOptions options) {
        if (!VotingTrigger.isBorderline(draft, findings, stats, summary)) {
            return false;
        }
        if (AppContext.get().ledger().exceeded(options.maxCostUsd())) {
            AppContext.get().ledger().noteCapSkip("score voting on a borderline review");
            return false;
        }
        return true;
    }
}
