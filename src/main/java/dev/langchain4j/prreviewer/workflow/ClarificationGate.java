package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ReviewMode;

/** Asks the human reviewer for clarifications, in interactive mode only. */
public interface ClarificationGate {

    @ConditionalAgent(name = "clarificationGate", description = "Asks clarifications in interactive mode",
            outputKey = Keys.REVIEWER_GUIDANCE, subAgents = Clarification.class)
    String clarify(ReviewMode mode);

    @ActivationCondition(value = Clarification.class, description = "interactive mode")
    static boolean interactive(ReviewMode mode) {
        return mode == ReviewMode.INTERACTIVE;
    }
}
