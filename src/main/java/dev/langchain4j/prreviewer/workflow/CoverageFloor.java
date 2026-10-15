package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.prreviewer.agents.CorrectnessReviewerFallback;
import dev.langchain4j.prreviewer.agents.DocsApiReviewerFallback;
import dev.langchain4j.prreviewer.agents.SecurityReviewerFallback;
import dev.langchain4j.prreviewer.model.DiffStats;
import java.util.Map;

/** Runs, in parallel, the essential reviewers the router did not select; see {@link ReviewerCoverage}. */
public interface CoverageFloor {

    @ConditionalAgent(name = "coverageFloor", description = "Runs the essential reviewers the router did not select",
            subAgents = {CorrectnessReviewerFallback.class, SecurityReviewerFallback.class, DocsApiReviewerFallback.class})
    void ensureCoverage(DiffStats diffStats);

    @ActivationCondition(value = CorrectnessReviewerFallback.class, description = "production code without correctness review")
    static boolean correctness(Map<String, Object> panelOutputs, DiffStats diffStats) {
        return ReviewerCoverage.correctnessMissing(panelOutputs, diffStats);
    }

    @ActivationCondition(value = SecurityReviewerFallback.class, description = "security-sensitive code without security review")
    static boolean security(Map<String, Object> panelOutputs, DiffStats diffStats) {
        return ReviewerCoverage.securityMissing(panelOutputs, diffStats);
    }

    @ActivationCondition(value = DocsApiReviewerFallback.class, description = "documentation-only change without documentation review")
    static boolean docs(Map<String, Object> panelOutputs, DiffStats diffStats) {
        return ReviewerCoverage.docsMissing(panelOutputs, diffStats);
    }
}
