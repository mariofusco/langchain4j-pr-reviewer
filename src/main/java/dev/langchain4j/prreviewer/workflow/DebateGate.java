package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.prreviewer.AppContext;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import java.util.List;

/** Debates only when some finding is contested, and only within the cost cap. */
public interface DebateGate {

    @ConditionalAgent(name = "debateGate", description = "Debates the contested findings",
            typedOutputKey = Keys.Findings.class, subAgents = ContestedDebates.class)
    List<Finding> debate(@K(Keys.Findings.class) List<Finding> findings);

    @ActivationCondition(value = ContestedDebates.class, description = "contested findings within the cost cap")
    static boolean contested(@K(Keys.ContestedFindings.class) List<Finding> queue,
                             @K(Keys.Options.class) ReviewOptions options) {
        return !queue.isEmpty() && !AppContext.get().ledger().exceeded(options.maxCostUsd());
    }
}
