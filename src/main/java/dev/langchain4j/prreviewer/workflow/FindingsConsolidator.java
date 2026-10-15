package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import dev.langchain4j.prreviewer.model.FileChange;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.RawFinding;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Non-AI agent: merges the findings of all reviewers into one validated, identified list. */
public class FindingsConsolidator {

    @Agent(name = "findingsConsolidator",
            description = "Validates, deduplicates and identifies the findings, and selects the doubtful ones for a debate",
            outputKey = Keys.FINDINGS)
    public static List<Finding> consolidate(AgenticScope scope, ReviewOptions options) {
        List<FileChange> changes = ScopeState.read(scope, Keys.CHANGES);
        Map<String, Object> panel = ScopeState.read(scope, Keys.PANEL_OUTPUTS);
        Map<String, List<RawFinding>> byReviewer = new LinkedHashMap<>();
        // a reviewer the router skipped may have been run by the coverage floor, under its fallback name
        byReviewer.put(ReviewerCoverage.reviewerOf("correctnessReviewer", panel),
                ScopeState.read(scope, Keys.CORRECTNESS_FINDINGS));
        byReviewer.put(ReviewerCoverage.reviewerOf("securityReviewer", panel), ScopeState.read(scope, Keys.SECURITY_FINDINGS));
        byReviewer.put("designReviewer", ScopeState.read(scope, Keys.DESIGN_FINDINGS));
        byReviewer.put("testReviewer", ScopeState.read(scope, Keys.TEST_FINDINGS));
        byReviewer.put(ReviewerCoverage.reviewerOf("docsApiReviewer", panel), ScopeState.read(scope, Keys.DOCS_FINDINGS));
        List<RawFinding> chunkFindings = new ArrayList<>();
        ScopeState.<List<List<RawFinding>>>read(scope, Keys.CHUNK_FINDINGS).forEach(list -> {
            if (list != null) {
                chunkFindings.addAll(list);
            }
        });
        byReviewer.put("chunkReviewer", chunkFindings);

        Consolidation.Result result = Consolidation.consolidate(byReviewer, changes, options.maxDebates());
        if (result.discarded() > 0) {
            ScopeState.append(scope, Keys.LIMITATIONS, result.discarded()
                    + " findings were discarded because their location is not part of the diff.");
        }
        scope.writeState(Keys.CONTESTED_FINDINGS, result.contested());
        return result.findings();
    }
}
