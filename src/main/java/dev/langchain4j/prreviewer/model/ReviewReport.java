package dev.langchain4j.prreviewer.model;

import java.util.List;

/** The review report. Its {@code toString()} is the Markdown rendering. */
public record ReviewReport(
        String pullRequest,
        String title,
        String summary,
        List<String> good,
        List<Finding> wrong,
        List<String> improvements,
        Scores scores,
        Verdict verdict,
        String verdictReasoning,
        double confidence,
        List<String> limitations,
        RunInfo run,
        String markdown) {

    @Override
    public String toString() {
        return markdown;
    }
}
