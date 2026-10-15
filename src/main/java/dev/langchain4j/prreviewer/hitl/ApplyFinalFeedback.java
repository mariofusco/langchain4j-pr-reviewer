package dev.langchain4j.prreviewer.hitl;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.Verification;
import java.util.List;

/**
 * Non-AI agent opening the finalization. When the revision loop ended at its limit while the reviewer was still
 * contesting, the drops are applied and the contested findings are marked unsure, since there is no round left to
 * debate them; the reviewer's notes are then applied by the finalizer like an edit.
 */
public class ApplyFinalFeedback {

    @Agent(name = "applyFinalFeedback", description = "Applies the last contest decision when no revision is left",
            outputKey = Keys.ACTIVE_FINDINGS)
    public static List<Finding> apply(AgenticScope scope, List<Finding> findings, ApprovalDecision approval) {
        List<Finding> updated = findings;
        if (approval.decision() == ApprovalDecision.Decision.CONTEST) {
            updated = HumanFeedback.applyDrops(findings, approval).stream()
                    .map(f -> approval.contestedFindingIds().contains(f.id()) && f.active()
                            ? f.withVerification(Verification.UNSURE)
                            : f)
                    .toList();
            if (!approval.contestedFindingIds().isEmpty()) {
                ScopeState.append(scope, Keys.LIMITATIONS, "Findings "
                        + String.join(", ", approval.contestedFindingIds())
                        + " were contested after the last revision round and could not be debated.");
            }
            scope.writeState(Keys.FINDINGS, updated);
        }
        return updated.stream().filter(Finding::active).toList();
    }
}
