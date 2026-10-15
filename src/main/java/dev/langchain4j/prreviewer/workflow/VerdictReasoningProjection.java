package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;

/**
 * Projection: declares the verdict reasoning written by the score calibrator, so that agents can read it as a
 * parameter.
 */
public class VerdictReasoningProjection {

    @Agent(name = "verdictReasoning", description = "Declares the verdict reasoning written by the score calibrator",
            outputKey = Keys.VERDICT_REASONING)
    public static String project(AgenticScope scope) {
        return ScopeState.read(scope, Keys.VERDICT_REASONING);
    }
}
