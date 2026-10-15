package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.RawFinding;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import java.util.List;

/** A reviewer of the panel. Its description is what the reviewer router reads to decide whether to activate it. */
public interface CorrectnessReviewer {

    @SystemMessage(fromResource = "/prompts/reviewer-system.md")
    @UserMessage(fromResource = "/prompts/correctness-user.md")
    @Agent(name = "correctnessReviewer",
            description = "Finds functional bugs in Java production code. Handles the request only if productionCodeChanged is true.",
            typedOutputKey = Keys.CorrectnessFindings.class)
    List<RawFinding> review(@K(Keys.ReviewBundle.class) String reviewBundle);

    @ChatModelSupplier
    static ChatModel model(AgenticScope scope) {
        return ModelSelection.select(scope, "correctnessReviewer", ModelTier.STANDARD, ModelTier.PREMIUM, Escalation::highRiskLargeOrDeep);
    }
}
