package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.voting.VotingPlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.prreviewer.agents.LenientScorer;
import dev.langchain4j.prreviewer.agents.NeutralScorer;
import dev.langchain4j.prreviewer.agents.SkepticalScorer;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ScoreCard;
import dev.langchain4j.prreviewer.scoring.ScoreCardVotingStrategy;
import java.util.List;

/** Three scorers with different perspectives and models vote; their cards are averaged, the verdict is the majority. */
public interface ScoreVoting {

    @PlannerAgent(name = "scoreVoting", description = "Aggregates independent score cards for a borderline review",
            outputKey = Keys.SCORE_CARD,
            subAgents = {NeutralScorer.class, SkepticalScorer.class, LenientScorer.class})
    ScoreCard vote(List<Finding> activeFindings);

    @PlannerSupplier
    static Planner planner() {
        return new VotingPlanner(ScoreCardVotingStrategy.averageAndMajority());
    }
}
