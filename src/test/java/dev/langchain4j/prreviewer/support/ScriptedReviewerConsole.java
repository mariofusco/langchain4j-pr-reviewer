package dev.langchain4j.prreviewer.support;

import dev.langchain4j.prreviewer.hitl.ReviewerConsole;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.PullRequest;
import dev.langchain4j.prreviewer.model.ReviewReport;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** A reviewer console with scripted answers, recording what it was shown. */
public final class ScriptedReviewerConsole implements ReviewerConsole {

    private final String guidance;
    private final Deque<ApprovalDecision> decisions;
    public final List<List<String>> questionsAsked = new ArrayList<>();
    public final List<ReviewReport> reportsShown = new ArrayList<>();
    public final List<List<Finding>> findingsShown = new ArrayList<>();

    public ScriptedReviewerConsole(String guidance, ApprovalDecision... decisions) {
        this.guidance = guidance;
        this.decisions = new ArrayDeque<>(List.of(decisions));
    }

    @Override
    public synchronized String askClarifications(PullRequest pr, PrSummary summary, List<String> questions) {
        questionsAsked.add(List.copyOf(questions));
        return guidance;
    }

    @Override
    public synchronized ApprovalDecision askApproval(ReviewReport report, List<Finding> findings, double spentUsd,
                                                     double capUsd, boolean debatesAffordable) {
        reportsShown.add(report);
        findingsShown.add(List.copyOf(findings));
        return decisions.isEmpty() ? ApprovalDecision.approved() : decisions.poll();
    }
}
