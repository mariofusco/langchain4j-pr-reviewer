package dev.langchain4j.prreviewer.models;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.app.ActiveReview;
import dev.langchain4j.prreviewer.config.ReviewDefaults;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import dev.langchain4j.prreviewer.model.ModelProvider;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.observability.CostLedger;
import java.util.function.Predicate;

/**
 * Scope-driven model selection used by the {@code @ChatModelSupplier} of every AI agent: the default tier, escalated
 * when the scope shows evidence for it, unless a tier is forced or the soft cost cap is reached. The provider comes
 * from the review options. The suppliers are also called once while the system is built, with every argument null:
 * then the catalog is looked up and the default provider's base tier returned.
 */
public final class ModelSelection {

    private ModelSelection() {}

    public static ChatModel fixed(ModelCatalog catalog, AgenticScope scope, ModelTier tier) {
        return select(catalog, scope, null, tier, tier, s -> false);
    }

    /** Like {@link #fixed}, for an agent that calls tools. */
    public static ChatModel fixedForTools(ModelCatalog catalog, AgenticScope scope, ModelTier tier) {
        return selectForTools(catalog, scope, null, tier, tier, s -> false);
    }

    public static ChatModel select(ModelCatalog catalog, AgenticScope scope, String agent, ModelTier base,
                                   ModelTier escalated, Predicate<AgenticScope> trigger) {
        return catalog(catalog).chatModel(provider(scope), tier(scope, agent, base, escalated, trigger));
    }

    /** Like {@link #select}, for an agent that calls tools. */
    public static ChatModel selectForTools(ModelCatalog catalog, AgenticScope scope, String agent, ModelTier base,
                                           ModelTier escalated, Predicate<AgenticScope> trigger) {
        return catalog(catalog).toolModel(provider(scope), tier(scope, agent, base, escalated, trigger));
    }

    static ModelTier tier(AgenticScope scope, String agent, ModelTier base, ModelTier escalated,
                          Predicate<AgenticScope> trigger) {
        if (scope == null) {
            return base;
        }
        ReviewOptions options = ScopeState.read(scope, Keys.OPTIONS);
        if (options != null && options.forcedTier() != null) {
            return options.forcedTier();
        }
        if (escalated == base || !trigger.test(scope)) {
            return base;
        }
        CostLedger ledger = ActiveReview.get().ledger();
        if (options != null && ledger.exceeded(options.maxCostUsd())) {
            ledger.noteCapSkip("model escalation of " + agent);
            return base;
        }
        return escalated;
    }

    private static ModelProvider provider(AgenticScope scope) {
        ReviewOptions options = scope == null ? null : ScopeState.read(scope, Keys.OPTIONS);
        return options != null ? options.provider() : ReviewDefaults.get().options().provider();
    }

    private static ModelCatalog catalog(ModelCatalog catalog) {
        return catalog != null ? catalog : ModelCatalog.get();
    }
}
