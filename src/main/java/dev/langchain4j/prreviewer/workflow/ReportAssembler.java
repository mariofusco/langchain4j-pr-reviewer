package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.AppContext;
import dev.langchain4j.prreviewer.keys.Keys;
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
import java.util.Set;

/** Non-AI agent: renders the report from the writer's prose and the review data. */
public class ReportAssembler {

    @Agent(name = "reportAssembler", description = "Renders the report from the prose and the review data",
            typedOutputKey = Keys.ReviewReport.class)
    public static ReviewReport assemble(AgenticScope scope,
                                        @K(Keys.PullRequest.class) PullRequest pr,
                                        @K(Keys.ReportDraft.class) ReportDraft draft,
                                        @K(Keys.ActiveFindings.class) List<Finding> findings,
                                        @K(Keys.Scores.class) Scores scores,
                                        @K(Keys.Verdict.class) Verdict verdict,
                                        @K(Keys.VerdictReasoning.class) String verdictReasoning) {
        return ReportRenderer.render(pr, draft, findings, scores, verdict, verdictReasoning,
                limitations(scope, draft), runInfo(scope));
    }

    /** Assembles the report from the current content of the scope. */
    static ReviewReport assemble(AgenticScope scope) {
        return assemble(scope,
                scope.readState(Keys.PullRequest.class),
                scope.readState(Keys.ReportDraft.class),
                scope.readState(Keys.ActiveFindings.class),
                scope.readState(Keys.Scores.class),
                scope.readState(Keys.Verdict.class),
                scope.readState(Keys.VerdictReasoning.class));
    }

    static List<String> limitations(AgenticScope scope, ReportDraft draft) {
        Set<String> limitations = new LinkedHashSet<>(scope.readState(Keys.Limitations.class));
        List<String> degraded = scope.readState(Keys.DegradedAgents.class);
        if (!degraded.isEmpty()) {
            limitations.add("These steps failed and were skipped or replaced by a fallback: "
                    + String.join(", ", degraded) + ".");
        }
        List<String> capEvents = AppContext.get().ledger().capEvents();
        if (!capEvents.isEmpty()) {
            limitations.add("Skipped because the cost cap was reached: " + String.join("; ", capEvents) + ".");
        }
        if (draft != null && draft.limitations() != null) {
            draft.limitations().stream().filter(l -> l != null && !l.isBlank()).forEach(limitations::add);
        }
        return List.copyOf(limitations);
    }

    static RunInfo runInfo(AgenticScope scope) {
        var panel = scope.readState(Keys.PanelOutputs.class);
        List<String> reviewers = new ArrayList<>(panel.keySet());
        reviewers.addAll(ReviewerCoverage.floorReviewers(panel, scope.readState(Keys.DiffStats.class)));
        if (!scope.readState(Keys.ChunkFindings.class).isEmpty()) {
            reviewers.add("chunkReviewer");
        }
        CostLedger ledger = AppContext.get().ledger();
        ReviewMode mode = scope.readState(Keys.Mode.class);
        return new RunInfo(mode, List.copyOf(reviewers), ledger.modelsByAgent(), ledger.total(),
                scope.readState(Keys.RevisionRound.class));
    }
}
