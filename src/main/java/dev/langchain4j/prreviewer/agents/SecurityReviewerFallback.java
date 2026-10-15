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

/** Part of the reviewer coverage floor: run when the router skipped it although the diff requires it. */
public interface SecurityReviewerFallback {

    @SystemMessage(fromResource = "/prompts/reviewer-system.md")
    @UserMessage(fromResource = "/prompts/security-user.md")
    @Agent(name = "securityReviewerFallback",
            description = "Reviews security vulnerabilities, run by the coverage floor when the router did not select it",
            typedOutputKey = Keys.SecurityFindings.class)
    List<RawFinding> review(@K(Keys.ReviewBundle.class) String reviewBundle);

    @ChatModelSupplier
    static ChatModel model(AgenticScope scope) {
        return ModelSelection.fixed(scope, ModelTier.STANDARD);
    }
}
