package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ToolProviderSupplier;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.github.GitHubMcp;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.models.ModelCatalog;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;

/** A debater about a contested finding; its name labels its turns in the debate context. */
public interface FindingDefender {

    int MAX_TOOL_CALLS = 6;

    @SystemMessage(fromResource = "/prompts/debate-system.md")
    @UserMessage(fromResource = "/prompts/defender-user.md")
    @Agent(name = "findingDefender", description = "Argues that a contested finding is a false positive", outputKey = Keys.DEFENSE)
    String argue(Finding contestedFinding,
                 String prOverview,
                 String contestedDiff,
                 String reviewerGuidance,
                 String debateContext);

    @ToolProviderSupplier
    static ToolProvider tools(@CdiBean GitHubMcp github) {
        return github.readTools(MAX_TOOL_CALLS);
    }

    @ChatModelSupplier
    static ChatModel model(@CdiBean ModelCatalog catalog, AgenticScope scope) {
        return ModelSelection.fixedForTools(catalog, scope, ModelTier.STANDARD);
    }
}
