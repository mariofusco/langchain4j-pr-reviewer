package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.app.ActiveReview;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import java.util.List;

/**
 * Debates the contested findings one at a time. A loop rather than a parallel mapper, because the debaters read the
 * finding under debate from the shared scope. The queue is capped at {@code maxDebates} by whoever fills it.
 */
public interface ContestedDebates {

    @LoopAgent(name = "contestedDebates", description = "Debates the contested findings one at a time",
            outputKey = Keys.FINDINGS, maxIterations = 10,
            subAgents = {NextContested.class, ContestedDiffProjection.class, DebateContextProjection.class,
                    FindingDebate.class, ApplyDebateVerdict.class})
    List<Finding> debate(List<Finding> findings);

    @ExitCondition(testExitAtLoopEnd = true, description = "no contested finding left, or cost cap reached")
    static boolean exit(AgenticScope scope, ReviewOptions options) {
        List<Finding> queue = ScopeState.read(scope, Keys.CONTESTED_FINDINGS);
        return queue.isEmpty() || ActiveReview.get().ledger().exceeded(options.maxCostUsd());
    }
}
