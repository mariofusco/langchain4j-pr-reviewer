package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.app.ActiveReview;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.PullRequest;
import dev.langchain4j.prreviewer.model.ReportDraft;
import dev.langchain4j.prreviewer.model.ReviewMode;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.model.RunInfo;
import dev.langchain4j.prreviewer.model.Scores;
import dev.langchain4j.prreviewer.model.Verdict;
import dev.langchain4j.prreviewer.observability.CostLedger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Non-AI agent: renders the report from the writer's prose and the review data. */
public class ReportAssembler {

    @Agent(name = "reportAssembler", description = "Renders the report from the prose and the review data",
            outputKey = Keys.REVIEW_REPORT)
    public static ReviewReport assemble(AgenticScope scope,
                                        PullRequest pullRequest,
                                        ReportDraft reportDraft,
                                        List<Finding> activeFindings,
                                        Scores scores,
                                        Verdict verdict,
                                        String verdictReasoning) {
        // counts the rounds of the report quality loop, whose exit condition lowers the bar after the first one
        scope.writeState(Keys.REPORT_ROUND, ScopeState.<Integer>read(scope, Keys.REPORT_ROUND) + 1);
        return ReportRenderer.render(pullRequest, reportDraft, activeFindings, scores, verdict, verdictReasoning,
                limitations(scope, reportDraft), runInfo(scope));
    }

    /** Assembles the report from the current content of the scope. */
    static ReviewReport assemble(AgenticScope scope) {
        return assemble(scope,
                ScopeState.read(scope, Keys.PULL_REQUEST),
                ScopeState.read(scope, Keys.REPORT_DRAFT),
                ScopeState.read(scope, Keys.ACTIVE_FINDINGS),
                ScopeState.read(scope, Keys.SCORES),
                ScopeState.read(scope, Keys.VERDICT),
                ScopeState.read(scope, Keys.VERDICT_REASONING));
    }

    static List<String> limitations(AgenticScope scope, ReportDraft draft) {
        Set<String> limitations = new LinkedHashSet<>(ScopeState.read(scope, Keys.LIMITATIONS));
        List<String> degraded = ScopeState.read(scope, Keys.DEGRADED_AGENTS);
        if (!degraded.isEmpty()) {
            limitations.add("These steps failed and were skipped or replaced by a fallback: "
                    + String.join(", ", degraded) + ".");
        }
        List<String> capEvents = ActiveReview.get().ledger().capEvents();
        if (!capEvents.isEmpty()) {
            limitations.add("Skipped because the cost cap was reached: " + String.join("; ", capEvents) + ".");
        }
        if (draft != null && draft.limitations() != null) {
            draft.limitations().stream().filter(l -> l != null && !l.isBlank()).forEach(limitations::add);
        }
        return List.copyOf(limitations);
    }

    static RunInfo runInfo(AgenticScope scope) {
        Map<String, Object> panel = ScopeState.read(scope, Keys.PANEL_OUTPUTS);
        List<String> reviewers = new ArrayList<>(panel.keySet());
        reviewers.addAll(ReviewerCoverage.floorReviewers(panel, ScopeState.read(scope, Keys.DIFF_STATS)));
        if (!ScopeState.<List<?>>read(scope, Keys.CHUNK_FINDINGS).isEmpty()) {
            reviewers.add("chunkReviewer");
        }
        CostLedger ledger = ActiveReview.get().ledger();
        ReviewMode mode = ScopeState.read(scope, Keys.MODE);
        return new RunInfo(mode, List.copyOf(reviewers), ledger.modelsByAgent(), ledger.total(),
                ScopeState.read(scope, Keys.REVISION_ROUND));
    }
}
