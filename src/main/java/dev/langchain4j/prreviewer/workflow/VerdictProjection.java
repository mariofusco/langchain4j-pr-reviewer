package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import dev.langchain4j.prreviewer.model.Verdict;

/** Projection: declares the verdict written by the score calibrator, so that agents can read it as a parameter. */
public class VerdictProjection {

    @Agent(name = "verdict", description = "Declares the verdict written by the score calibrator",
            outputKey = Keys.VERDICT)
    public static Verdict project(AgenticScope scope) {
        return ScopeState.read(scope, Keys.VERDICT);
    }
}
