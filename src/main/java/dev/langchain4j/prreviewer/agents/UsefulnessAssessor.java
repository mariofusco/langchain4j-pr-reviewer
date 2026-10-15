package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.UsefulnessAssessment;
import dev.langchain4j.prreviewer.models.ModelCatalog;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;

/** Runs asynchronously, while the review panel works: only the scorers wait for its result. */
public interface UsefulnessAssessor {

    @SystemMessage(fromResource = "/prompts/analyst-system.md")
    @UserMessage(fromResource = "/prompts/usefulness-user.md")
    @Agent(name = "usefulnessAssessor", description = "Assesses how useful the contribution is for the project",
            outputKey = Keys.USEFULNESS, async = true)
    UsefulnessAssessment assess(String prOverview, PrSummary prSummary);

    @ChatModelSupplier
    static ChatModel model(@CdiBean ModelCatalog catalog, AgenticScope scope) {
        return ModelSelection.select(catalog, scope, "usefulnessAssessor", ModelTier.ECONOMY, ModelTier.STANDARD,
                Escalation::featureOrRefactoring);
    }
}
