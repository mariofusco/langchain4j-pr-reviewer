package dev.langchain4j.prreviewer.model;

import java.util.List;

/** The triage of a pull request. Its {@code toString()} is the rendering used in prompts. */
public record PrSummary(
        String intent,
        Category category,
        Size size,
        Risk risk,
        List<String> areasTouched,
        boolean needsContext,
        List<String> openQuestions) {

    @Override
    public String toString() {
        return "Intent: " + intent
                + "\nCategory: " + category + ", size: " + size + ", risk: " + risk
                + "\nAreas touched: " + (areasTouched == null || areasTouched.isEmpty() ? "-" : String.join("; ", areasTouched))
                + "\nOpen questions: " + (openQuestions == null || openQuestions.isEmpty() ? "-" : String.join(" | ", openQuestions));
    }
}
