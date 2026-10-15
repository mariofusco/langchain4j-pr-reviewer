package dev.langchain4j.prreviewer.model;

import java.util.List;

/** How useful the contribution is. Its {@code toString()} is the rendering used in prompts. */
public record UsefulnessAssessment(double score, String rationale, boolean matchesLinkedIssue, List<String> concerns) {

    public static UsefulnessAssessment unknown() {
        return new UsefulnessAssessment(5.0, "Usefulness could not be assessed.", false, List.of());
    }

    @Override
    public String toString() {
        return "Usefulness " + score + "/10: " + rationale
                + (matchesLinkedIssue ? " Matches the linked issue." : "")
                + (concerns == null || concerns.isEmpty() ? "" : " Concerns: " + String.join("; ", concerns));
    }
}
