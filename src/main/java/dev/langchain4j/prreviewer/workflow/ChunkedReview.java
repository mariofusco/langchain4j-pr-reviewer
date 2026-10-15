package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.prreviewer.agents.ChunkReviewer;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.RawFinding;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Map step of the chunked review: one chunk reviewer per chunk, in parallel. */
public interface ChunkedReview {

    @ParallelMapperAgent(name = "chunkedReview", description = "Reviews each chunk of left-out files in parallel",
            typedOutputKey = Keys.ChunkFindings.class, subAgent = ChunkReviewer.class, itemsProvider = "DiffChunks")
    List<List<RawFinding>> review(@K(Keys.DiffChunks.class) List<String> chunks);

    @ParallelExecutor
    static Executor executor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
