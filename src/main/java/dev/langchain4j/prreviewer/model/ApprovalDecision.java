package dev.langchain4j.prreviewer.model;

import java.util.List;

/** What the human reviewer decided about a report. */
public record ApprovalDecision(
        Decision decision, String notes, List<String> contestedFindingIds, List<String> droppedFindingIds) {

    public enum Decision {
        APPROVED,
        EDIT,
        CONTEST,
        REJECTED
    }

    public static ApprovalDecision approved() {
        return new ApprovalDecision(Decision.APPROVED, "", List.of(), List.of());
    }

    public boolean hasNotes() {
        return notes != null && !notes.isBlank();
    }
}
