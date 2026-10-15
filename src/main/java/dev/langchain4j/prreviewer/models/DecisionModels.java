package dev.langchain4j.prreviewer.models;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.prreviewer.model.DecisionProvider;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * The decision models behind the reviewer router, configured as named typesafe models: {@code local} (System One API
 * on a local server, Ollama serving {@code tev1:0.8b} by default), {@code hosted} (TypeSafe) and {@code openrouter}.
 */
@ApplicationScoped
@Unremovable
public class DecisionModels {

    @Inject
    @Any
    Instance<DecisionModel> models;

    public static DecisionModels get() {
        return Arc.container().instance(DecisionModels.class).get();
    }

    public DecisionModel model(DecisionProvider provider) {
        return models.select(ModelName.Literal.of(provider.configName())).get();
    }

    /** Whether the decision model's key is set, when it needs one. */
    public static boolean available(DecisionProvider provider) {
        return provider.apiKeyVariable() == null
                || ConfigProvider.getConfig().getOptionalValue(provider.apiKeyVariable(), String.class)
                        .filter(key -> !key.isBlank())
                        .isPresent();
    }
}
