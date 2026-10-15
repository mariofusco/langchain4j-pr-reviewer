package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.models.ModelCatalog;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;

public interface PrTriage {

    @SystemMessage(fromResource = "/prompts/analyst-system.md")
    @UserMessage(fromResource = "/prompts/triage-user.md")
    @Agent(name = "prTriage", description = "Classifies the pull request: intent, category, size, risk and open questions",
            outputKey = Keys.PR_SUMMARY)
    PrSummary triage(String prOverview, String diffText);

    @ChatModelSupplier
    static ChatModel model(@CdiBean ModelCatalog catalog, AgenticScope scope) {
        return ModelSelection.fixed(catalog, scope, ModelTier.ECONOMY);
    }
}
