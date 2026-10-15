package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import java.util.List;

public interface QuestionGenerator {

    @SystemMessage(fromResource = "/prompts/analyst-system.md")
    @UserMessage(fromResource = "/prompts/questions-user.md")
    @Agent(name = "questionGenerator",
            description = "Prepares up to three questions for the human reviewer whose answers would change the review",
            typedOutputKey = Keys.ClarificationQuestions.class)
    List<String> questions(@K(Keys.PrOverview.class) String overview,
                           @K(Keys.PrSummary.class) PrSummary summary,
                           @K(Keys.CodeContext.class) String codeContext);

    @ChatModelSupplier
    static ChatModel model(AgenticScope scope) {
        return ModelSelection.fixed(scope, ModelTier.ECONOMY);
    }
}
