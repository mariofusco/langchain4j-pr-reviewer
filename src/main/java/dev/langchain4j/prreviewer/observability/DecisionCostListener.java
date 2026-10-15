package dev.langchain4j.prreviewer.observability;

import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelResponseContext;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Records the usage of the reviewer router's decision model. It has no entry in the price table, so its cost is
 * zero: exact for a local model, unknown for a hosted one.
 */
public final class DecisionCostListener implements DecisionModelListener {

    private final CostLedger ledger;

    public DecisionCostListener(CostLedger ledger) {
        this.ledger = ledger;
    }

    @Override
    public void onResponse(DecisionModelResponseContext context) {
        DecisionResponse response = context.decisionResponse();
        TokenUsage usage = response.tokenUsage();
        long input = usage == null || usage.inputTokenCount() == null ? 0 : usage.inputTokenCount();
        long output = usage == null || usage.outputTokenCount() == null ? 0 : usage.outputTokenCount();
        String model = response.modelName() == null ? "decision-model" : response.modelName();
        ledger.record("reviewPanel (decision model)", model, input, output, 0, 0);
    }
}
