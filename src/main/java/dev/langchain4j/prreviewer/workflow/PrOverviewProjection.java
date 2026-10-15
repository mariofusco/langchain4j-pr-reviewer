package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;

/**
 * Projection: declares the pull request overview written by the diff preprocessor, so that agents can read it as a
 * parameter.
 */
public class PrOverviewProjection {

    @Agent(name = "prOverview", description = "Declares the pull request overview written by the diff preprocessor",
            outputKey = Keys.PR_OVERVIEW)
    public static String project(AgenticScope scope) {
        return ScopeState.read(scope, Keys.PR_OVERVIEW);
    }
}
