package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.RawFinding;
import dev.langchain4j.prreviewer.models.ModelCatalog;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import java.util.List;

/**
 * Reviews one chunk of the files left out of the main diff, for correctness and security. Mapped over the chunks:
 * the chunk is injected as the first argument, and each result is collected under its output key.
 */
public interface ChunkReviewer {

    @SystemMessage(fromResource = "/prompts/analyst-system.md")
    @UserMessage(fromResource = "/prompts/chunk-user.md")
    @Agent(name = "chunkReviewer", description = "Reviews one chunk of a very large diff for correctness and security",
            outputKey = "ChunkReview")
    List<RawFinding> review(String diffChunk, String prOverview, PrSummary prSummary);

    @ChatModelSupplier
    static ChatModel model(@CdiBean ModelCatalog catalog, AgenticScope scope) {
        return ModelSelection.fixed(catalog, scope, ModelTier.STANDARD);
    }
}
