package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.patterns.debate.DebatePlanner;
import dev.langchain4j.agentic.scope.AgenticScope;

/**
 * Projection: declares the transcript the debate planner keeps for the debaters and the judge (reset by nextContested
 * before each debate), so that agents can read it as a parameter.
 */
public class DebateContextProjection {

    @Agent(name = "debateContext", description = "Declares the transcript of the debate",
            outputKey = DebatePlanner.DEBATE_CONTEXT_KEY)
    public static String project(AgenticScope scope) {
        return scope.readState(DebatePlanner.DEBATE_CONTEXT_KEY, "");
    }
}
