package dev.langchain4j.prreviewer.scoring;

import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.FindingCategory;
import dev.langchain4j.prreviewer.model.Severity;
import dev.langchain4j.prreviewer.model.Verdict;
import java.util.List;
import java.util.Optional;

/** The verdict floor: the least strict verdict the findings allow. The model may be stricter, never more lenient. */
public final class VerdictRules {

    /** The floor and the rule that set it, empty for {@link Verdict#APPROVE}. */
    public record Floor(Verdict verdict, String rule, Finding decidingFinding) {}

    private VerdictRules() {}

    public static Floor floor(List<Finding> findings) {
        Optional<Finding> blocking = findings.stream()
                .filter(Findings::credible)
                .filter(f -> f.severity().atLeast(Severity.HIGH))
                .filter(f -> f.category() == FindingCategory.CORRECTNESS || f.category() == FindingCategory.SECURITY)
                .min((a, b) -> Integer.compare(a.severity().ordinal(), b.severity().ordinal()));
        if (blocking.isPresent()) {
            Finding f = blocking.get();
            return new Floor(Verdict.REQUEST_CHANGES,
                    f.severity() + " " + f.category().name().toLowerCase() + " finding " + f.id(), f);
        }
        Optional<Finding> notable = findings.stream()
                .filter(f -> f.active() && f.severity().atLeast(Severity.MEDIUM))
                .min((a, b) -> Integer.compare(a.severity().ordinal(), b.severity().ordinal()));
        return notable.map(f -> new Floor(Verdict.COMMENT,
                        f.severity() + " " + f.category().name().toLowerCase() + " finding " + f.id(), f))
                .orElseGet(() -> new Floor(Verdict.APPROVE, "", null));
    }

    public static Verdict apply(Verdict proposed, Floor floor) {
        return Verdict.stricter(proposed == null ? Verdict.COMMENT : proposed, floor.verdict());
    }

    public static String explain(String proposedReasoning, Verdict proposed, Floor floor, Verdict verdict) {
        String reasoning = proposedReasoning == null ? "" : proposedReasoning.strip();
        if (verdict != proposed && floor.verdict() == verdict) {
            return (reasoning.isEmpty() ? "" : reasoning + " ")
                    + "Rule applied: " + floor.rule() + ", so the verdict is at least " + verdict + ".";
        }
        return reasoning;
    }
}
