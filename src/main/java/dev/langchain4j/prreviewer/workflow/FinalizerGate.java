package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.ReviewReport;

/**
 * Applies the reviewer's edits; also applies a last contest that came after the final revision round, which can
 * no longer be debated.
 */
public interface FinalizerGate {

    @ConditionalAgent(name = "finalizerGate", description = "Applies the human reviewer's edits",
            outputKey = Keys.REVIEW_REPORT, subAgents = Finalization.class)
    ReviewReport finalizeReport(ApprovalDecision approval);

    @ActivationCondition(value = Finalization.class, description = "edits requested, or contest after the last round")
    static boolean edits(ApprovalDecision approval) {
        return approval.decision() == ApprovalDecision.Decision.CONTEST
                || (approval.decision() == ApprovalDecision.Decision.EDIT && approval.hasNotes());
    }
}
