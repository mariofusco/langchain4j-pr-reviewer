package dev.langchain4j.prreviewer.model;

import java.util.List;

/** The critic's assessment of a report, from 0.0 to 1.0. */
public record Critique(double score, List<String> issues) {

    public static Critique none() {
        return new Critique(0.0, List.of());
    }

    @Override
    public String toString() {
        return issues == null || issues.isEmpty()
                ? "(no critique yet)"
                : "Critique score " + score + ". Issues to fix:\n- " + String.join("\n- ", issues);
    }
}
