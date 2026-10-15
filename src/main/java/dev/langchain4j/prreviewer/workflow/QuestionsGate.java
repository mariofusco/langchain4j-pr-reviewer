package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.prreviewer.hitl.HumanClarification;
import dev.langchain4j.prreviewer.keys.Keys;
import java.util.List;

/** Bothers the human reviewer only when there is something to ask. */
public interface QuestionsGate {

    @ConditionalAgent(name = "questionsGate", description = "Asks the questions when there are any",
            outputKey = Keys.REVIEWER_GUIDANCE, subAgents = HumanClarification.class)
    String ask(List<String> clarificationQuestions);

    @ActivationCondition(value = HumanClarification.class, description = "there are questions")
    static boolean hasQuestions(List<String> clarificationQuestions) {
        return !clarificationQuestions.isEmpty();
    }
}
