package dev.langchain4j.prreviewer.support;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.RawFinding;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stand-in for the chunk reviewer, as a class with a static agent method. A parallel mapper cannot map an agent built
 * with {@code nonAiAgentBuilder} in langchain4j-agentic 1.22.0-beta32: its invoker passes the item positionally where
 * the scope function expects the scope.
 */
public class StubChunkReviewer {

    static final AtomicInteger INVOCATIONS = new AtomicInteger();
    /** When set, the chunk containing this text fails, like a malformed model answer would. */
    static volatile String failIfContains;

    @Agent(name = "chunkReviewer", description = "stub of chunkReviewer", outputKey = "ChunkReview")
    public static List<RawFinding> review(@K(Keys.DiffChunk.class) String chunk) {
        if (!chunk.contains("### File: ")) {
            throw new IllegalArgumentException("not a diff chunk: " + chunk);
        }
        if (failIfContains != null && chunk.contains(failIfContains)) {
            throw new IllegalStateException("chunk reviewer failed");
        }
        INVOCATIONS.incrementAndGet();
        return List.of(StubAgents.raw(dev.langchain4j.prreviewer.model.FindingCategory.CORRECTNESS,
                dev.langchain4j.prreviewer.model.Severity.LOW, firstFile(chunk), 0, 0.6));
    }

    private static String firstFile(String chunk) {
        int start = chunk.indexOf("### File: ") + "### File: ".length();
        return chunk.substring(start, chunk.indexOf(' ', start));
    }
}
