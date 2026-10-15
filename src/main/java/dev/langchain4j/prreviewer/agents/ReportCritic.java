package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Critique;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import java.util.List;

public interface ReportCritic {

    @SystemMessage(fromResource = "/prompts/analyst-system.md")
    @UserMessage(fromResource = "/prompts/report-critic-user.md")
    @Agent(name = "reportCritic", description = "Checks the report for accuracy, consistency and actionability",
            typedOutputKey = Keys.ReportCritique.class)
    Critique critique(@K(Keys.ReviewReport.class) ReviewReport report, @K(Keys.ActiveFindings.class) List<Finding> findings);

    @ChatModelSupplier
    static ChatModel model(AgenticScope scope) {
        return ModelSelection.fixed(scope, ModelTier.ECONOMY);
    }
}
