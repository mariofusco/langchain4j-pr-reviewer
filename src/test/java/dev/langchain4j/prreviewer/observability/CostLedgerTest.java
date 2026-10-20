package dev.langchain4j.prreviewer.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CostLedgerTest {

    @Test
    void prices_tokens_per_model_with_cache_rates_and_the_haiku_long_prompt_step() {
        assertThat(PriceTable.cost("claude-sonnet-5-5", 1_000_000, 100_000, 0, 0)).isCloseTo(3.0, within(1e-9));
        assertThat(PriceTable.cost("claude-opus-5-5", 0, 0, 1_000_000, 0)).isCloseTo(0.20, within(1e-9));
        assertThat(PriceTable.cost("claude-haiku-5-5", 50_000, 1_000, 0, 0)).isCloseTo(0.0055, within(1e-9));
        assertThat(PriceTable.cost("claude-haiku-5-5", 150_000, 1_000, 0, 0)).isCloseTo(0.0775, within(1e-9));
        assertThat(PriceTable.cost("claude-sonnet-5-5-20260901", 1_000_000, 0, 0, 0)).isCloseTo(2.0, within(1e-9));
        assertThat(PriceTable.cost("tev1:0.8b", 1_000_000, 1_000_000, 0, 0)).isZero();
        // the longest known prefix wins: a nano snapshot is not priced as gpt-5, and gpt-5.1 is not priced as gpt-5
        assertThat(PriceTable.cost("gpt-5-nano-2025-08-07", 1_000_000, 0, 0, 0)).isCloseTo(0.05, within(1e-9));
        assertThat(PriceTable.cost("gpt-5.1-2025-11-13", 1_000_000, 0, 0, 0)).isCloseTo(1.25, within(1e-9));
        assertThat(PriceTable.cost("gpt-5", 1_000_000, 0, 0, 0)).isCloseTo(1.25, within(1e-9));
        assertThat(PriceTable.cost("gpt-5-mini", 0, 0, 1_000_000, 0)).isCloseTo(0.025, within(1e-9));
        assertThat(PriceTable.price("gpt-55")).isNull();
        // gpt-6: cache writes are billed, and prompts above 272K tokens are priced at 2x input and 1.5x output
        assertThat(PriceTable.cost("gpt-6-luna", 1_000_000, 0, 0, 0)).isCloseTo(0.20, within(1e-9));
        assertThat(PriceTable.cost("gpt-6-luna", 100_000, 10_000, 0, 0)).isCloseTo(0.015, within(1e-9));
        assertThat(PriceTable.cost("gpt-6-luna", 0, 0, 0, 1_000_000)).isCloseTo(0.125, within(1e-9));
        assertThat(PriceTable.cost("gpt-6.1-sol", 100_000, 10_000, 0, 0)).isCloseTo(0.30, within(1e-9));
        assertThat(PriceTable.cost("gpt-6.1-sol", 300_000, 10_000, 0, 0)).isCloseTo(1.35, within(1e-9));
    }

    @Test
    void accumulates_concurrently_per_agent_and_model_and_reports_the_cap() {
        CostLedger ledger = new CostLedger();
        IntStream.range(0, 1000).parallel()
                .forEach(i -> ledger.record(i % 2 == 0 ? "a" : "b", "claude-sonnet-5-5", 1000, 100, 0, 0));

        assertThat(ledger.entries()).hasSize(2);
        assertThat(ledger.entries()).allSatisfy(e -> assertThat(e.calls()).isEqualTo(500));
        assertThat(ledger.total()).isCloseTo(1000 * 0.003, within(1e-9));
        assertThat(ledger.exceeded(2.0)).isTrue();
        assertThat(ledger.exceeded(5.0)).isFalse();
        assertThat(ledger.exceeded(0)).isFalse();
        ledger.noteCapSkip("debate of F1");
        ledger.noteCapSkip("debate of F1");
        assertThat(ledger.capEvents()).containsExactly("debate of F1");
        assertThat(ledger.summary()).contains("total: $3.0000");
    }
}
