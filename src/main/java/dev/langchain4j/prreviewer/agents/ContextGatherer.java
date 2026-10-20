package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.github.GitHubTools;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.models.ModelCatalog;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;

public interface ContextGatherer {

    @SystemMessage(fromResource = "/prompts/analyst-system.md")
    @UserMessage(fromResource = "/prompts/context-gatherer-user.md")
    @ToolBox(GitHubTools.class)
    @Agent(name = "contextGatherer",
            description = "Reads the repository to collect the code around the change: callers, interfaces, existing tests",
            outputKey = Keys.CODE_CONTEXT)
    String gather(String prOverview, PrSummary prSummary, String diffText);

    @ChatModelSupplier
    static ChatModel model(@CdiBean ModelCatalog catalog, AgenticScope scope) {
        return ModelSelection.selectForTools(catalog, scope, "contextGatherer", ModelTier.ECONOMY, ModelTier.STANDARD,
                Escalation::riskAtLeastMedium);
    }
}
