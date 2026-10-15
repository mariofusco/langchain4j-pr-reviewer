package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;

/**
 * Projection: declares the changes of the contested finding's file, written by nextContested, so that agents can read
 * it as a parameter.
 */
public class ContestedDiffProjection {

    @Agent(name = "contestedDiff",
            description = "Declares the changes of the contested finding's file, written by nextContested",
            outputKey = Keys.CONTESTED_DIFF)
    public static String project(AgenticScope scope) {
        return ScopeState.read(scope, Keys.CONTESTED_DIFF);
    }
}
