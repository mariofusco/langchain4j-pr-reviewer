package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.prreviewer.agents.QuestionGenerator;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.PrSummary;

public interface Clarification {

    @SequenceAgent(name = "clarification", description = "Generates questions and asks them to the human reviewer",
            outputKey = Keys.REVIEWER_GUIDANCE, subAgents = {QuestionGenerator.class, QuestionsGate.class})
    String clarify(PrSummary prSummary);
}
