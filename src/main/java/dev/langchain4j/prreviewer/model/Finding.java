package dev.langchain4j.prreviewer.model;

/**
 * A validated finding with a stable id (F1, F2, ...) that the reviewer can refer to.
 * Its {@code toString()} is the rendering used in prompts.
 */
public record Finding(
        String id,
        String reviewer,
        FindingCategory category,
        Severity severity,
        String file,
        int line,
        String title,
        String explanation,
        String suggestion,
        String evidence,
        double confidence,
        Verification verification) {

    public static Finding from(String id, String reviewer, RawFinding raw) {
        return new Finding(
                id,
                reviewer,
                raw.category(),
                raw.severity(),
                raw.file(),
                Math.max(raw.line(), 0),
                raw.title(),
                raw.explanation(),
                raw.suggestion(),
                raw.evidence(),
                Math.clamp(raw.confidence(), 0.0, 1.0),
                Verification.UNVERIFIED);
    }

    /** Findings rejected by a debate or dropped by the human reviewer no longer count. */
    public boolean active() {
        return verification != Verification.REJECTED && verification != Verification.DROPPED_BY_REVIEWER;
    }

    public String location() {
        return line > 0 ? file + ":" + line : file;
    }

    public Finding withVerification(Verification newVerification) {
        return new Finding(id, reviewer, category, severity, file, line, title, explanation, suggestion, evidence,
                confidence, newVerification);
    }

    public Finding withSeverity(Severity newSeverity) {
        return new Finding(id, reviewer, category, newSeverity, file, line, title, explanation, suggestion, evidence,
                confidence, verification);
    }

    @Override
    public String toString() {
        return "\n- " + id + " [" + severity + "][" + category + "] " + location() + " - " + title
                + ". " + explanation
                + (evidence == null || evidence.isBlank() ? "" : " Evidence: `" + evidence.strip() + "`.")
                + (suggestion == null || suggestion.isBlank() ? "" : " Suggestion: " + suggestion)
                + " (confidence " + confidence + ", " + verification + ", from " + reviewer + ")";
    }
}
