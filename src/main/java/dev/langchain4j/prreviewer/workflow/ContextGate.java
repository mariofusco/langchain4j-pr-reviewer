package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.prreviewer.agents.ContextGatherer;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.PrSummary;

/** Gathers repository context only when the triage says the diff alone is not enough. */
public interface ContextGate {

    @ConditionalAgent(name = "contextGate", description = "Gathers code context when the triage asks for it",
            outputKey = Keys.CODE_CONTEXT, subAgents = ContextGatherer.class)
    String gather(PrSummary prSummary);

    @ActivationCondition(value = ContextGatherer.class, description = "the triage needs context")
    static boolean needsContext(PrSummary prSummary) {
        return prSummary.needsContext();
    }
}
