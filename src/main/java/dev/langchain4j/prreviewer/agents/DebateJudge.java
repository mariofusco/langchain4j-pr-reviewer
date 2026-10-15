package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.DebateVerdict;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.models.ModelCatalog;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;

/** The judge of a debate: always the last sub-agent of the debate planner. */
public interface DebateJudge {

    @SystemMessage(fromResource = "/prompts/analyst-system.md")
    @UserMessage(fromResource = "/prompts/judge-user.md")
    @Agent(name = "debateJudge", description = "Decides whether a contested finding is real from the debate",
            outputKey = Keys.DEBATE_VERDICT)
    DebateVerdict judge(Finding contestedFinding, String debateContext);

    @ChatModelSupplier
    static ChatModel model(@CdiBean ModelCatalog catalog, AgenticScope scope) {
        return ModelSelection.select(catalog, scope, "debateJudge", ModelTier.STANDARD, ModelTier.PREMIUM,
                Escalation::criticalFindingContested);
    }
}
