package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;

/**
 * Projection: declares the reviewed diff written by the diff preprocessor, so that agents can read it as a parameter.
 */
public class DiffTextProjection {

    @Agent(name = "diffText", description = "Declares the reviewed diff written by the diff preprocessor",
            outputKey = Keys.DIFF_TEXT)
    public static String project(AgenticScope scope) {
        return ScopeState.read(scope, Keys.DIFF_TEXT);
    }
}
