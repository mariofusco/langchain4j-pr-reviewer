package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.prreviewer.app.ActiveReview;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import java.util.List;

/** Debates only when some finding is contested, and only within the cost cap. */
public interface DebateGate {

    @ConditionalAgent(name = "debateGate", description = "Debates the contested findings",
            outputKey = Keys.FINDINGS, subAgents = ContestedDebates.class)
    List<Finding> debate(List<Finding> findings);

    @ActivationCondition(value = ContestedDebates.class, description = "contested findings within the cost cap")
    static boolean contested(List<Finding> contestedFindings, ReviewOptions options) {
        return !contestedFindings.isEmpty() && !ActiveReview.get().ledger().exceeded(options.maxCostUsd());
    }
}
