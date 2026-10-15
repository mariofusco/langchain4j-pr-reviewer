package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.prreviewer.AppContext;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.RawFinding;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import java.util.List;

/** Reviews in chunks the files of a very large pull request that did not fit in the main diff. */
public interface LargeDiffGate {

    @ConditionalAgent(name = "largeDiffGate", description = "Reviews the left-out files of a very large pull request",
            typedOutputKey = Keys.ChunkFindings.class, subAgents = ChunkedReview.class)
    List<List<RawFinding>> review(@K(Keys.DiffChunks.class) List<String> chunks);

    @ActivationCondition(value = ChunkedReview.class, description = "files were left out of the main diff")
    static boolean hasChunks(@K(Keys.DiffChunks.class) List<String> chunks, @K(Keys.Options.class) ReviewOptions options) {
        if (chunks.isEmpty()) {
            return false;
        }
        if (AppContext.get().ledger().exceeded(options.maxCostUsd())) {
            AppContext.get().ledger().noteCapSkip("chunked review of " + chunks.size() + " left-out parts");
            return false;
        }
        return true;
    }
}
