package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.decisionrouter.DecisionRouterPlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.prreviewer.agents.CorrectnessReviewer;
import dev.langchain4j.prreviewer.agents.DesignReviewer;
import dev.langchain4j.prreviewer.agents.DocsApiReviewer;
import dev.langchain4j.prreviewer.agents.SecurityReviewer;
import dev.langchain4j.prreviewer.agents.TestReviewer;
import dev.langchain4j.prreviewer.app.ActiveReview;
import dev.langchain4j.prreviewer.config.ReviewDefaults;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.models.DecisionModels;
import java.util.Map;

/**
 * The reviewer router. A decision model answers, in one call, one yes/no question per reviewer ("should the agent
 * 'securityReviewer' (its description) handle this request?"), and every reviewer whose probability reaches the
 * threshold runs in parallel. The result maps each activated reviewer to its findings, and is empty when no reviewer
 * is relevant. Only the compact routing input reaches the decision model, never the diff.
 */
public interface ReviewPanel {

    @PlannerAgent(name = "reviewPanel", description = "Selects the relevant reviewers and runs them in parallel",
            outputKey = Keys.PANEL_OUTPUTS,
            subAgents = {CorrectnessReviewer.class, SecurityReviewer.class, DesignReviewer.class, TestReviewer.class,
                    DocsApiReviewer.class})
    Map<String, Object> review(Map<String, Object> routingInput);

    /** Called once per review, with that review's decision model and threshold; at build time, with the defaults. */
    @PlannerSupplier
    static Planner planner() {
        ReviewOptions options = ActiveReview.get().current()
                .map(run -> run.request().options())
                .orElseGet(() -> ReviewDefaults.get().options());
        return new DecisionRouterPlanner(DecisionModels.get().model(options.decisionModel()), options.routerThreshold());
    }
}
