package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.prreviewer.keys.Keys;

/**
 * Declares the key of the chunk each chunk reviewer reads. The parallel mapper passes the chunk to the reviewer's
 * invocation directly, not through the scope, but the Quarkus build-time validation requires a declared producer for
 * every agent parameter. This agent writes nothing useful: an empty chunk, before the chunked review.
 */
public class DiffChunkDeclaration {

    @Agent(name = "diffChunk", description = "Declares the chunk key the chunked review binds to each chunk reviewer",
            outputKey = Keys.DIFF_CHUNK)
    public static String declare() {
        return "";
    }
}
