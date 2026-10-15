package dev.langchain4j.prreviewer.hitl;

import dev.langchain4j.prreviewer.model.ApprovalDecision;
import java.util.Locale;
import java.util.Optional;

/** The rules every human channel applies to an approval decision before the review acts on it. */
public final class ApprovalRules {

    private ApprovalRules() {}

    /** Why the decision cannot be accepted, or empty when it can. */
    public static Optional<String> refusal(ApprovalDecision decision, boolean debatesAffordable, double spentUsd,
                                           double capUsd) {
        if (decision.decision() == ApprovalDecision.Decision.CONTEST && !decision.contestedFindingIds().isEmpty()
                && !debatesAffordable) {
            return Optional.of(String.format(Locale.ROOT, "The cost cap of $%.2f is reached ($%.2f spent): findings "
                    + "can no longer be debated. Use drop or edit instead.", capUsd, spentUsd));
        }
        return Optional.empty();
    }
}
