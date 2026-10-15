package dev.langchain4j.prreviewer.models;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.AppContext;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import java.util.function.Predicate;

/**
 * Scope-driven model selection used by the {@code @ChatModelSupplier} of every AI agent: the default tier, escalated
 * when the scope shows evidence for it, unless a tier is forced or the soft cost cap is reached.
 */
public final class ModelSelection {

    private ModelSelection() {}

    public static ChatModel fixed(AgenticScope scope, ModelTier tier) {
        return select(scope, null, tier, tier, s -> false);
    }

    /** Like {@link #fixed}, for an agent that calls tools. */
    public static ChatModel fixedForTools(AgenticScope scope, ModelTier tier) {
        return selectForTools(scope, null, tier, tier, s -> false);
    }

    public static ChatModel select(AgenticScope scope, String agent, ModelTier base, ModelTier escalated,
                                   Predicate<AgenticScope> trigger) {
        return AppContext.get().models().model(tier(scope, agent, base, escalated, trigger));
    }

    /** Like {@link #select}, for an agent that calls tools. */
    public static ChatModel selectForTools(AgenticScope scope, String agent, ModelTier base, ModelTier escalated,
                                           Predicate<AgenticScope> trigger) {
        return AppContext.get().models().toolModel(tier(scope, agent, base, escalated, trigger));
    }

    static ModelTier tier(AgenticScope scope, String agent, ModelTier base, ModelTier escalated,
                          Predicate<AgenticScope> trigger) {
        if (scope == null) {
            // the framework also calls the supplier once without a scope, while building the agent
            return base;
        }
        ReviewOptions options = scope.readState(Keys.Options.class);
        if (options != null && options.forcedTier() != null) {
            return options.forcedTier();
        }
        if (escalated == base || !trigger.test(scope)) {
            return base;
        }
        AppContext context = AppContext.get();
        if (options != null && context.ledger().exceeded(options.maxCostUsd())) {
            context.ledger().noteCapSkip("model escalation of " + agent);
            return base;
        }
        return escalated;
    }
}
