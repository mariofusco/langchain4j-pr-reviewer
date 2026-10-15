package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeLists;
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
            typedOutputKey = Keys.Findings.class)
    public static List<Finding> consolidate(AgenticScope scope,
                                            @K(Keys.Changes.class) List<FileChange> changes,
                                            @K(Keys.Options.class) ReviewOptions options) {
        Map<String, Object> panel = scope.readState(Keys.PanelOutputs.class);
        Map<String, List<RawFinding>> byReviewer = new LinkedHashMap<>();
        // a reviewer the router skipped may have been run by the coverage floor, under its fallback name
        byReviewer.put(ReviewerCoverage.reviewerOf("correctnessReviewer", panel),
                scope.readState(Keys.CorrectnessFindings.class));
        byReviewer.put(ReviewerCoverage.reviewerOf("securityReviewer", panel), scope.readState(Keys.SecurityFindings.class));
        byReviewer.put("designReviewer", scope.readState(Keys.DesignFindings.class));
        byReviewer.put("testReviewer", scope.readState(Keys.TestFindings.class));
        byReviewer.put(ReviewerCoverage.reviewerOf("docsApiReviewer", panel), scope.readState(Keys.DocsFindings.class));
        List<RawFinding> chunkFindings = new ArrayList<>();
        scope.readState(Keys.ChunkFindings.class).forEach(list -> {
            if (list != null) {
                chunkFindings.addAll(list);
            }
        });
        byReviewer.put("chunkReviewer", chunkFindings);

        Consolidation.Result result = Consolidation.consolidate(byReviewer, changes, options.maxDebates());
        if (result.discarded() > 0) {
            ScopeLists.append(scope, Keys.Limitations.class, result.discarded()
                    + " findings were discarded because their location is not part of the diff.");
        }
        scope.writeState(Keys.ContestedFindings.class, result.contested());
        return result.findings();
    }
}
