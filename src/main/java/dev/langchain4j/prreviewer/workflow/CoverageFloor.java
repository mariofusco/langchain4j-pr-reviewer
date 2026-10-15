package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.prreviewer.agents.CorrectnessReviewerFallback;
import dev.langchain4j.prreviewer.agents.DocsApiReviewerFallback;
import dev.langchain4j.prreviewer.agents.SecurityReviewerFallback;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.DiffStats;
import java.util.Map;

/** Runs, in parallel, the essential reviewers the router did not select; see {@link ReviewerCoverage}. */
public interface CoverageFloor {

    @ConditionalAgent(name = "coverageFloor", description = "Runs the essential reviewers the router did not select",
            subAgents = {CorrectnessReviewerFallback.class, SecurityReviewerFallback.class, DocsApiReviewerFallback.class})
    void ensureCoverage(@K(Keys.DiffStats.class) DiffStats stats);

    @ActivationCondition(value = CorrectnessReviewerFallback.class, description = "production code without correctness review")
    static boolean correctness(@K(Keys.PanelOutputs.class) Map<String, Object> panel, @K(Keys.DiffStats.class) DiffStats stats) {
        return ReviewerCoverage.correctnessMissing(panel, stats);
    }

    @ActivationCondition(value = SecurityReviewerFallback.class, description = "security-sensitive code without security review")
    static boolean security(@K(Keys.PanelOutputs.class) Map<String, Object> panel, @K(Keys.DiffStats.class) DiffStats stats) {
        return ReviewerCoverage.securityMissing(panel, stats);
    }

    @ActivationCondition(value = DocsApiReviewerFallback.class, description = "documentation-only change without documentation review")
    static boolean docs(@K(Keys.PanelOutputs.class) Map<String, Object> panel, @K(Keys.DiffStats.class) DiffStats stats) {
        return ReviewerCoverage.docsMissing(panel, stats);
    }
}
