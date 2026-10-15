package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.ToolProviderSupplier;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.AppContext;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolProvider;

public interface ContextGatherer {

    int MAX_TOOL_CALLS = 12;

    @SystemMessage(fromResource = "/prompts/analyst-system.md")
    @UserMessage(fromResource = "/prompts/context-gatherer-user.md")
    @Agent(name = "contextGatherer",
            description = "Reads the repository to collect the code around the change: callers, interfaces, existing tests",
            typedOutputKey = Keys.CodeContext.class)
    String gather(@K(Keys.PrOverview.class) String overview,
                  @K(Keys.PrSummary.class) PrSummary summary,
                  @K(Keys.DiffText.class) String diff);

    @ToolProviderSupplier
    static ToolProvider tools() {
        return AppContext.get().githubTools();
    }

    @ChatModelSupplier
    static ChatModel model(AgenticScope scope) {
        return ModelSelection.selectForTools(scope, "contextGatherer", ModelTier.ECONOMY, ModelTier.STANDARD,
                Escalation::riskAtLeastMedium);
    }
}
