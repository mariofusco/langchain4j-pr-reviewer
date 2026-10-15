package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import java.util.List;

/** Projection: declares the limitations collected so far, so that agents can read it as a parameter. */
public class LimitationsProjection {

    @Agent(name = "limitations", description = "Declares the limitations collected so far",
            outputKey = Keys.LIMITATIONS)
    public static List<String> project(AgenticScope scope) {
        return ScopeState.read(scope, Keys.LIMITATIONS);
    }
}
