package dev.langchain4j.prreviewer.observability;

import java.util.Map;

/**
 * List prices in USD per million tokens (October 2026). Anthropic cache writes use the 5-minute TTL rate; OpenAI
 * charges nothing extra for cache writes, and its cached rate applies to the cached part of the prompt.
 */
public final class PriceTable {

    /** Prices of one model; some models have a higher rate for prompts above a threshold (e.g. 100K for Haiku 5.5, 272K for gpt-6). */
    public record Price(double input, double output, double cacheRead, double cacheWrite,
                        double longInput, double longOutput, int longPromptThreshold) {}

    private static final Map<String, Price> PRICES = Map.ofEntries(
            Map.entry("claude-haiku-5-5", new Price(0.10, 0.50, 0.01, 0.125, 0.50, 2.50, 100_000)),
            Map.entry("claude-sonnet-5-5", new Price(2.00, 10.00, 0.20, 2.50, 2.00, 10.00, Integer.MAX_VALUE)),
            Map.entry("claude-opus-5-5", new Price(4.00, 20.00, 0.20, 5.00, 4.00, 20.00, Integer.MAX_VALUE)),
            Map.entry("gpt-5-nano", new Price(0.05, 0.40, 0.005, 0.0, 0.05, 0.40, Integer.MAX_VALUE)),
            Map.entry("gpt-5-mini", new Price(0.25, 2.00, 0.025, 0.0, 0.25, 2.00, Integer.MAX_VALUE)),
            Map.entry("gpt-5.1", new Price(1.25, 10.00, 0.125, 0.0, 1.25, 10.00, Integer.MAX_VALUE)),
            Map.entry("gpt-5", new Price(1.25, 10.00, 0.125, 0.0, 1.25, 10.00, Integer.MAX_VALUE)),
            Map.entry("gpt-5.4-nano", new Price(0.20, 1.25, 0.02, 0.0, 0.20, 1.25, Integer.MAX_VALUE)),
            Map.entry("gpt-5.4-mini", new Price(0.75, 4.50, 0.075, 0.0, 0.75, 4.50, Integer.MAX_VALUE)),
            Map.entry("gpt-5.6-luna", new Price(0.20, 1.20, 0.02, 0.0, 0.20, 1.20, Integer.MAX_VALUE)),
            Map.entry("gpt-6-luna", new Price(0.10, 0.50, 0.01, 0.125, 0.20, 0.75, 272_000)),
            Map.entry("gpt-6.1-sol", new Price(2.00, 10.00, 0.10, 2.50, 4.00, 15.00, 272_000)),
            Map.entry("gpt-4.1-nano", new Price(0.10, 0.40, 0.025, 0.0, 0.10, 0.40, Integer.MAX_VALUE)),
            Map.entry("gpt-4.1-mini", new Price(0.40, 1.60, 0.10, 0.0, 0.40, 1.60, Integer.MAX_VALUE)));

    private PriceTable() {}

    /**
     * The price of the model, matched by the longest known prefix so that snapshot suffixes still resolve and
     * {@code gpt-5-nano-2025-08-07} is not priced as {@code gpt-5}; null when unknown.
     */
    public static Price price(String model) {
        if (model == null) {
            return null;
        }
        return PRICES.entrySet().stream()
                .filter(e -> model.equals(e.getKey()) || model.startsWith(e.getKey() + "-"))
                .max(java.util.Comparator.comparingInt(e -> e.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    public static double cost(String model, long input, long output, long cacheRead, long cacheWrite) {
        Price price = price(model);
        if (price == null) {
            return 0.0;
        }
        boolean longPrompt = input + cacheRead + cacheWrite > price.longPromptThreshold();
        double inputRate = longPrompt ? price.longInput() : price.input();
        double outputRate = longPrompt ? price.longOutput() : price.output();
        return (input * inputRate + output * outputRate + cacheRead * price.cacheRead() + cacheWrite * price.cacheWrite())
                / 1_000_000.0;
    }
}
