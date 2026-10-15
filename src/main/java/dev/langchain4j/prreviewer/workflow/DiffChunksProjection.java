package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import java.util.List;

/**
 * Projection: declares the left-out diff chunks written by the diff preprocessor, so that agents can read it as a
 * parameter.
 */
public class DiffChunksProjection {

    @Agent(name = "diffChunks", description = "Declares the left-out diff chunks written by the diff preprocessor",
            outputKey = Keys.DIFF_CHUNKS)
    public static List<String> project(AgenticScope scope) {
        return ScopeState.read(scope, Keys.DIFF_CHUNKS);
    }
}
