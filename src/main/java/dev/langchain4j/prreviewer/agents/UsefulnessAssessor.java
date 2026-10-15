package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.UsefulnessAssessment;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/** Runs asynchronously, while the review panel works: only the scorers wait for its result. */
public interface UsefulnessAssessor {

    @SystemMessage(fromResource = "/prompts/analyst-system.md")
    @UserMessage(fromResource = "/prompts/usefulness-user.md")
    @Agent(name = "usefulnessAssessor", description = "Assesses how useful the contribution is for the project",
            typedOutputKey = Keys.Usefulness.class, async = true)
    UsefulnessAssessment assess(@K(Keys.PrOverview.class) String overview, @K(Keys.PrSummary.class) PrSummary summary);

    @ChatModelSupplier
    static ChatModel model(AgenticScope scope) {
        return ModelSelection.select(scope, "usefulnessAssessor", ModelTier.ECONOMY, ModelTier.STANDARD,
                Escalation::featureOrRefactoring);
    }
}
