package dev.langchain4j.prreviewer.hitl;

import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.Verification;
import java.util.List;

/** Applies the reviewer's contest and drop decisions to the findings. */
final class HumanFeedback {

    private HumanFeedback() {}

    static List<Finding> applyDrops(List<Finding> findings, ApprovalDecision approval) {
        return findings.stream()
                .map(f -> approval.droppedFindingIds().contains(f.id())
                        ? f.withVerification(Verification.DROPPED_BY_REVIEWER)
                        : f)
                .toList();
    }

    static List<Finding> contested(List<Finding> findings, ApprovalDecision approval, int max) {
        return findings.stream()
                .filter(Finding::active)
                .filter(f -> approval.contestedFindingIds().contains(f.id()))
                .limit(max)
                .toList();
    }

    static String guidanceNote(ApprovalDecision approval, int round) {
        StringBuilder note = new StringBuilder("Human reviewer, revision ").append(round).append(':');
        if (!approval.contestedFindingIds().isEmpty()) {
            note.append(" contested ").append(String.join(", ", approval.contestedFindingIds())).append('.');
        }
        if (!approval.droppedFindingIds().isEmpty()) {
            note.append(" dropped ").append(String.join(", ", approval.droppedFindingIds())).append('.');
        }
        if (approval.hasNotes()) {
            note.append(" Notes: ").append(approval.notes().strip());
        }
        return note.toString();
    }
}
