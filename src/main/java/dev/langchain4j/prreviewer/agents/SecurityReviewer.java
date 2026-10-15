package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.RawFinding;
import dev.langchain4j.prreviewer.models.ModelCatalog;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import java.util.List;

/** A reviewer of the panel. Its description is what the reviewer router reads to decide whether to activate it. */
public interface SecurityReviewer {

    @SystemMessage(fromResource = "/prompts/reviewer-system.md")
    @UserMessage(fromResource = "/prompts/security-user.md")
    @Agent(name = "securityReviewer",
            description = "Finds security vulnerabilities such as injection, unsafe deserialization or leaked secrets. Handles the request only if touchesSecuritySensitiveCode is true.",
            outputKey = Keys.SECURITY_FINDINGS)
    List<RawFinding> review(String reviewBundle);

    @ChatModelSupplier
    static ChatModel model(@CdiBean ModelCatalog catalog, AgenticScope scope) {
        return ModelSelection.select(catalog, scope, "securityReviewer", ModelTier.STANDARD, ModelTier.PREMIUM, Escalation::securitySensitiveOrDeep);
    }
}
