package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.patterns.debate.DebatePlanner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.DebateVerdict;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/** The judge of a debate: always the last sub-agent of the debate planner. */
public interface DebateJudge {

    @SystemMessage(fromResource = "/prompts/analyst-system.md")
    @UserMessage(fromResource = "/prompts/judge-user.md")
    @Agent(name = "debateJudge", description = "Decides whether a contested finding is real from the debate",
            typedOutputKey = Keys.DebateVerdict.class)
    DebateVerdict judge(@K(Keys.ContestedFinding.class) Finding finding,
                        @V(DebatePlanner.DEBATE_CONTEXT_KEY) String debateContext);

    @ChatModelSupplier
    static ChatModel model(AgenticScope scope) {
        return ModelSelection.select(scope, "debateJudge", ModelTier.STANDARD, ModelTier.PREMIUM,
                Escalation::criticalFindingContested);
    }
}
