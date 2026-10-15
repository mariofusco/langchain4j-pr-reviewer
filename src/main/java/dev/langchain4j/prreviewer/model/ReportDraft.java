package dev.langchain4j.prreviewer.model;

import java.util.List;

/**
 * The prose of the report as written by the report writer. The findings, scores and verdict are rendered by code.
 *
 * @param wrongFindingIds ids of the findings listed under "What is wrong", most important first
 */
public record ReportDraft(
        String summary,
        List<String> good,
        List<String> wrongFindingIds,
        List<String> improvements,
        List<String> limitations) {

    public static ReportDraft none() {
        return new ReportDraft("", List.of(), List.of(), List.of(), List.of());
    }

    @Override
    public String toString() {
        if (summary == null || summary.isBlank()) {
            return "(no previous draft)";
        }
        return "Summary: " + summary
                + "\nGood: " + good
                + "\nWrong (finding ids): " + wrongFindingIds
                + "\nImprovements: " + improvements
                + "\nLimitations: " + limitations;
    }
}
