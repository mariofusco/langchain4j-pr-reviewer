package dev.langchain4j.prreviewer.hitl;

import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.PullRequest;
import dev.langchain4j.prreviewer.model.ReviewReport;
import java.util.List;

/** The human reviewer, asked synchronously. */
public interface ReviewerConsole {

    /** Asks the clarification questions; returns the guidance for the reviewers, possibly empty. */
    String askClarifications(PullRequest pr, PrSummary summary, List<String> questions);

    /**
     * Shows the report and asks for a decision.
     *
     * @param debatesAffordable false once the soft cost cap is reached, when contesting is no longer possible
     */
    ApprovalDecision askApproval(ReviewReport report, List<Finding> findings, double spentUsd, double capUsd,
                                 boolean debatesAffordable);
}
