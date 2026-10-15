package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.decisionrouter.DecisionRouterPlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.prreviewer.AppContext;
import dev.langchain4j.prreviewer.agents.CorrectnessReviewer;
import dev.langchain4j.prreviewer.agents.DesignReviewer;
import dev.langchain4j.prreviewer.agents.DocsApiReviewer;
import dev.langchain4j.prreviewer.agents.SecurityReviewer;
import dev.langchain4j.prreviewer.agents.TestReviewer;
import dev.langchain4j.prreviewer.keys.Keys;
import java.util.Map;

/**
 * The reviewer router. A decision model answers, in one call, one yes/no question per reviewer ("should the agent
 * 'securityReviewer' (its description) handle this request?"), and every reviewer whose probability reaches the
 * threshold runs in parallel. The result maps each activated reviewer to its findings, and is empty when no reviewer
 * is relevant. Only the compact routing input reaches the decision model, never the diff.
 */
public interface ReviewPanel {

    @PlannerAgent(name = "reviewPanel", description = "Selects the relevant reviewers and runs them in parallel",
            typedOutputKey = Keys.PanelOutputs.class,
            subAgents = {CorrectnessReviewer.class, SecurityReviewer.class, DesignReviewer.class, TestReviewer.class,
                    DocsApiReviewer.class})
    Map<String, Object> review(@K(Keys.RoutingInput.class) Map<String, Object> routingInput);

    @PlannerSupplier
    static Planner planner() {
        AppContext context = AppContext.get();
        return new DecisionRouterPlanner(context.decisionModel(), context.options().routerThreshold());
    }
}
