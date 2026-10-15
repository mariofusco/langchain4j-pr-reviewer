package dev.langchain4j.prreviewer.observability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Token usage and cost of a review, per agent and model. Thread safe: parallel agents record concurrently. */
public final class CostLedger {

    /** Usage of one agent on one model. */
    public record Entry(String agent, String model, int calls, long inputTokens, long outputTokens,
                        long cacheReadTokens, long cacheWriteTokens, double costUsd) {

        Entry plus(long input, long output, long cacheRead, long cacheWrite, double cost) {
            return new Entry(agent, model, calls + 1, inputTokens + input, outputTokens + output,
                    cacheReadTokens + cacheRead, cacheWriteTokens + cacheWrite, costUsd + cost);
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final List<String> capEvents = Collections.synchronizedList(new ArrayList<>());

    public void record(String agent, String model, long input, long output, long cacheRead, long cacheWrite) {
        double cost = PriceTable.cost(model, input, output, cacheRead, cacheWrite);
        String key = agent + "|" + model;
        entries.compute(key, (k, existing) -> (existing == null
                        ? new Entry(agent, model, 0, 0, 0, 0, 0, 0.0)
                        : existing)
                .plus(input, output, cacheRead, cacheWrite, cost));
    }

    public double total() {
        return entries.values().stream().mapToDouble(Entry::costUsd).sum();
    }

    /** Whether the soft cost cap is reached; a cap of zero or less disables it. */
    public boolean exceeded(double capUsd) {
        return capUsd > 0 && total() >= capUsd;
    }

    /** Records that a step was skipped because of the cost cap, once per description. */
    public void noteCapSkip(String description) {
        synchronized (capEvents) {
            if (!capEvents.contains(description)) {
                capEvents.add(description);
            }
        }
    }

    public List<String> capEvents() {
        synchronized (capEvents) {
            return List.copyOf(capEvents);
        }
    }

    public List<Entry> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparing(Entry::agent).thenComparing(Entry::model))
                .toList();
    }

    /** Agent name to the model ids it used, comma separated. */
    public Map<String, String> modelsByAgent() {
        Map<String, List<String>> models = new TreeMap<>();
        entries().forEach(e -> models.computeIfAbsent(e.agent(), a -> new ArrayList<>()).add(e.model()));
        Map<String, String> result = new LinkedHashMap<>();
        models.forEach((agent, list) -> result.put(agent, String.join(", ", list)));
        return result;
    }

    public long totalCacheReadTokens() {
        return entries.values().stream().mapToLong(Entry::cacheReadTokens).sum();
    }

    public String summary() {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%-34s %-20s %5s %9s %8s %9s %9s%n",
                "agent", "model", "calls", "input", "output", "cache rd", "cost $"));
        for (Entry e : entries()) {
            sb.append(String.format(Locale.ROOT, "%-34s %-20s %5d %9d %8d %9d %9.4f%n", e.agent(), e.model(),
                    e.calls(), e.inputTokens(), e.outputTokens(), e.cacheReadTokens(), e.costUsd()));
        }
        sb.append(String.format(Locale.ROOT, "total: $%.4f%n", total()));
        return sb.toString();
    }
}
