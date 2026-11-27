package dev.langchain4j.prreviewer.model;

/**
 * A finding as returned by a reviewer model, before validation and identification.
 *
 * <p>{@code line} and {@code confidence} are boxed so that a model that leaves them out, or answers {@code null}, loses
 * only that value and not the reviewer's whole answer: the JSON mapper refuses {@code null} for a primitive. The
 * compact constructor, which the mapper calls, puts the defaults in, so the accessors never return {@code null}. A
 * missing title is taken from the explanation, the suggestion or the evidence.
 *
 * @param line       the line in the new version of the file, 0 when the finding concerns the whole file (the default)
 * @param evidence   the exact code from the diff that supports the finding
 * @param confidence from 0.0 to 1.0; {@value #UNKNOWN_CONFIDENCE} when the model gives none, below the debate threshold,
 *                   so that a contested finding without one is debated
 */
public record RawFinding(
        FindingCategory category,
        Severity severity,
        String file,
        Integer line,
        String title,
        String explanation,
        String suggestion,
        String evidence,
        Double confidence) {

    public static final double UNKNOWN_CONFIDENCE = 0.5;
    static final int MAX_DERIVED_TITLE = 100;

    public RawFinding {
        if (line == null) {
            line = 0;
        }
        if (confidence == null || confidence.isNaN()) {
            confidence = UNKNOWN_CONFIDENCE;
        }
        if (title == null || title.isBlank()) {
            title = derivedTitle(explanation, suggestion, evidence);
        }
    }

    /** The first sentence of the first non-blank text, shortened; empty when there is none. */
    private static String derivedTitle(String... texts) {
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                String first = text.strip().split("(?<=[.!?])\\s+|\\R", 2)[0];
                if (first.endsWith(".")) {
                    first = first.substring(0, first.length() - 1);
                }
                return first.length() <= MAX_DERIVED_TITLE ? first : first.substring(0, MAX_DERIVED_TITLE - 1).strip() + "…";
            }
        }
        return "";
    }
}
