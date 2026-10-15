package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.prreviewer.hitl.HumanClarification;
import dev.langchain4j.prreviewer.keys.Keys;
import java.util.List;

/** Bothers the human reviewer only when there is something to ask. */
public interface QuestionsGate {

    @ConditionalAgent(name = "questionsGate", description = "Asks the questions when there are any",
            typedOutputKey = Keys.ReviewerGuidance.class, subAgents = HumanClarification.class)
    String ask(@K(Keys.ClarificationQuestions.class) List<String> questions);

    @ActivationCondition(value = HumanClarification.class, description = "there are questions")
    static boolean hasQuestions(@K(Keys.ClarificationQuestions.class) List<String> questions) {
        return !questions.isEmpty();
    }
}
