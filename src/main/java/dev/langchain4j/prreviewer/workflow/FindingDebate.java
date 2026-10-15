package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.debate.ConvergenceStrategy;
import dev.langchain4j.agentic.patterns.debate.DebatePlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.prreviewer.agents.DebateJudge;
import dev.langchain4j.prreviewer.agents.FindingDefender;
import dev.langchain4j.prreviewer.agents.FindingProsecutor;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.DebateVerdict;
import dev.langchain4j.prreviewer.model.Finding;

/**
 * An adversarial debate on one finding: prosecutor and defender argue in parallel for up to two rounds, stopping
 * early when both end with the same word, then the judge, always last, decides.
 */
public interface FindingDebate {

    int MAX_ROUNDS = 2;

    @PlannerAgent(name = "findingDebate", description = "Settles a contested finding through a debate",
            outputKey = Keys.DEBATE_VERDICT,
            subAgents = {FindingProsecutor.class, FindingDefender.class, DebateJudge.class})
    DebateVerdict debate(Finding contestedFinding);

    @PlannerSupplier
    static Planner planner() {
        return new DebatePlanner(MAX_ROUNDS, ConvergenceStrategy.unanimousLastWord());
    }
}
