package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.prreviewer.agents.ContextGatherer;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.PrSummary;

/** Gathers repository context only when the triage says the diff alone is not enough. */
public interface ContextGate {

    @ConditionalAgent(name = "contextGate", description = "Gathers code context when the triage asks for it",
            typedOutputKey = Keys.CodeContext.class, subAgents = ContextGatherer.class)
    String gather(@K(Keys.PrSummary.class) PrSummary summary);

    @ActivationCondition(value = ContextGatherer.class, description = "the triage needs context")
    static boolean needsContext(@K(Keys.PrSummary.class) PrSummary summary) {
        return summary.needsContext();
    }
}
