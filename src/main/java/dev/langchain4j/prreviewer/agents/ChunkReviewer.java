package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.RawFinding;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
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
    List<RawFinding> review(@K(Keys.DiffChunk.class) String chunk,
                            @K(Keys.PrOverview.class) String overview,
                            @K(Keys.PrSummary.class) PrSummary summary);

    @ChatModelSupplier
    static ChatModel model(AgenticScope scope) {
        return ModelSelection.fixed(scope, ModelTier.STANDARD);
    }
}
