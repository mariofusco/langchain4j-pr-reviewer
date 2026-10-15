package dev.langchain4j.prreviewer.observability;

import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelResponseContext;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.prreviewer.app.ActiveReview;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Records the usage of the reviewer router's decision model. It has no entry in the price table, so its cost is
 * zero: exact for a local model, unknown for a hosted one.
 */
@ApplicationScoped
public class DecisionCostListener implements DecisionModelListener {

    @Inject
    ActiveReview activeReview;

    @Override
    public void onResponse(DecisionModelResponseContext context) {
        DecisionResponse response = context.decisionResponse();
        TokenUsage usage = response.tokenUsage();
        long input = usage == null || usage.inputTokenCount() == null ? 0 : usage.inputTokenCount();
        long output = usage == null || usage.outputTokenCount() == null ? 0 : usage.outputTokenCount();
        String model = response.modelName() == null ? "decision-model" : response.modelName();
        activeReview.ledger().record("reviewPanel (decision model)", model, input, output, 0, 0);
    }
}
