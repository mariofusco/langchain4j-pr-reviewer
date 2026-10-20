package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.github.GitHubTools;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.models.ModelCatalog;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;

/** A debater about a contested finding; its name labels its turns in the debate context. */
public interface FindingProsecutor {

    @SystemMessage(fromResource = "/prompts/debate-system.md")
    @UserMessage(fromResource = "/prompts/prosecutor-user.md")
    @ToolBox(GitHubTools.class)
    @Agent(name = "findingProsecutor", description = "Argues that a contested finding is real", outputKey = Keys.PROSECUTION)
    String argue(Finding contestedFinding,
                 String prOverview,
                 String contestedDiff,
                 String reviewerGuidance,
                 String debateContext);

    @ChatModelSupplier
    static ChatModel model(@CdiBean ModelCatalog catalog, AgenticScope scope) {
        return ModelSelection.fixedForTools(catalog, scope, ModelTier.STANDARD);
    }
}
