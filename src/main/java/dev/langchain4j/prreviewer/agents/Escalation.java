package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Category;
import dev.langchain4j.prreviewer.model.Critique;
import dev.langchain4j.prreviewer.model.DiffStats;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.Risk;
import dev.langchain4j.prreviewer.model.Severity;
import dev.langchain4j.prreviewer.model.Size;

/** The evidence in the scope that justifies a stronger model. */
final class Escalation {

    private Escalation() {}

    static boolean riskAtLeastMedium(AgenticScope scope) {
        PrSummary s = scope.readState(Keys.PrSummary.class);
        return s != null && s.risk() != Risk.LOW;
    }

    static boolean highRiskLargeOrDeep(AgenticScope scope) {
        PrSummary s = scope.readState(Keys.PrSummary.class);
        return deep(scope) || (s != null && (s.risk() == Risk.HIGH || s.size() == Size.LARGE));
    }

    static boolean securitySensitiveOrDeep(AgenticScope scope) {
        DiffStats stats = scope.readState(Keys.DiffStats.class);
        return deep(scope) || (stats != null && stats.touchesSecuritySensitive());
    }

    static boolean largeOrRefactoring(AgenticScope scope) {
        PrSummary s = scope.readState(Keys.PrSummary.class);
        return s != null && (s.size() == Size.LARGE || s.category() == Category.REFACTOR);
    }

    static boolean featureOrRefactoring(AgenticScope scope) {
        PrSummary s = scope.readState(Keys.PrSummary.class);
        return s != null && (s.category() == Category.FEATURE || s.category() == Category.REFACTOR);
    }

    static boolean criticalFindingContested(AgenticScope scope) {
        Finding f = scope.readState(Keys.ContestedFinding.class);
        return f != null && f.severity() == Severity.CRITICAL;
    }

    /** A critique exists only after the first draft of the current revision failed to pass. */
    static boolean previousDraftCriticized(AgenticScope scope) {
        Critique critique = scope.readState(Keys.ReportCritique.class);
        return critique != null && !critique.issues().isEmpty();
    }

    private static boolean deep(AgenticScope scope) {
        ReviewOptions options = scope.readState(Keys.Options.class);
        return options != null && options.deep();
    }
}
