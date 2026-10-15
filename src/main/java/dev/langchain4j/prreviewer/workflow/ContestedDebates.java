package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.prreviewer.AppContext;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import java.util.List;

/**
 * Debates the contested findings one at a time. A loop rather than a parallel mapper, because the debaters read the
 * finding under debate from the shared scope. The queue is capped at {@code maxDebates} by whoever fills it.
 */
public interface ContestedDebates {

    @LoopAgent(name = "contestedDebates", description = "Debates the contested findings one at a time",
            typedOutputKey = Keys.Findings.class, maxIterations = 10,
            subAgents = {NextContested.class, FindingDebate.class, ApplyDebateVerdict.class})
    List<Finding> debate(@K(Keys.ContestedFindings.class) List<Finding> queue);

    @ExitCondition(testExitAtLoopEnd = true, description = "no contested finding left, or cost cap reached")
    static boolean exit(@K(Keys.ContestedFindings.class) List<Finding> queue,
                        @K(Keys.Options.class) ReviewOptions options) {
        return queue.isEmpty() || AppContext.get().ledger().exceeded(options.maxCostUsd());
    }
}
