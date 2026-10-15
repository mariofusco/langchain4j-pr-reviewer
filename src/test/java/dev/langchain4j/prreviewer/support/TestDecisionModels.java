package dev.langchain4j.prreviewer.support;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.prreviewer.observability.DecisionCostListener;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Replaces every named decision model with the {@link TestScenario}'s fake. Excluded by the profiles of the integration
 * tests that use the real decision models.
 */
@ApplicationScoped
public class TestDecisionModels {

    @Inject
    TestScenario scenario;

    @Inject
    DecisionCostListener decisionCosts;

    @Produces
    @ApplicationScoped
    @ModelName("local")
    DecisionModel local() {
        return decisionModel();
    }

    @Produces
    @ApplicationScoped
    @ModelName("hosted")
    DecisionModel hosted() {
        return decisionModel();
    }

    @Produces
    @ApplicationScoped
    @ModelName("openrouter")
    DecisionModel openRouter() {
        return decisionModel();
    }

    private DecisionModel decisionModel() {
        return new DecisionModel() {
            @Override
            public DecisionResponse doDecide(DecisionRequest request) {
                return scenario.decisionModel().doDecide(request);
            }

            @Override
            public List<DecisionModelListener> listeners() {
                return List.of(decisionCosts);
            }
        };
    }
}
