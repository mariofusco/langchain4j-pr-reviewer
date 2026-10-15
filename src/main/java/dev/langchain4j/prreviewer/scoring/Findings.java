package dev.langchain4j.prreviewer.scoring;

import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.FindingCategory;
import dev.langchain4j.prreviewer.model.Severity;
import dev.langchain4j.prreviewer.model.Verification;
import java.util.List;
import java.util.Optional;

/** Queries on findings shared by the scoring rules. */
public final class Findings {

    /** An unverified or unsettled finding counts in the rules only from this confidence on. */
    public static final double CONFIDENCE_THRESHOLD = 0.7;

    private Findings() {}

    /**
     * Whether the finding may drive a score cap or the verdict floor: confirmed by a debate, or reported with enough
     * confidence and never put in doubt. A finding a debate could not settle, or that the reviewer contested after the
     * last revision round, is still reported but no longer decides the verdict.
     */
    public static boolean credible(Finding f) {
        if (!f.active()) {
            return false;
        }
        return f.verification() == Verification.CONFIRMED
                || (f.verification() == Verification.UNVERIFIED && f.confidence() >= CONFIDENCE_THRESHOLD);
    }

    /** The most severe credible finding of the category with at least the given severity. */
    public static Optional<Finding> worst(List<Finding> findings, FindingCategory category, Severity minimum) {
        return findings.stream()
                .filter(Findings::credible)
                .filter(f -> f.category() == category && f.severity().atLeast(minimum))
                .min((a, b) -> Integer.compare(a.severity().ordinal(), b.severity().ordinal()));
    }

    public static boolean anyActiveAtLeast(List<Finding> findings, Severity minimum) {
        return findings.stream().anyMatch(f -> f.active() && f.severity().atLeast(minimum));
    }
}
