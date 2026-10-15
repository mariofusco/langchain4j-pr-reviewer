package dev.langchain4j.prreviewer.model;

/**
 * Options of one review run.
 *
 * @param deep            escalate correctness and security to the premium tier
 * @param maxFiles        maximum number of files rendered into the reviewed diff
 * @param maxDiffTokens   token budget of the reviewed diff; files beyond it go to the chunked review
 * @param forcedTier      when not null, every AI agent uses this tier
 * @param maxDebates      maximum number of contested findings debated per revision
 * @param routerThreshold activation threshold of the reviewer router, strictly between 0 and 1
 * @param maxCostUsd      soft cost cap: past it voting, debates and escalation are skipped
 * @param maxChunks       maximum number of diff chunks reviewed for very large pull requests
 * @param provider        chat model provider of every AI agent
 * @param decisionModel   decision model of the reviewer router
 */
public record ReviewOptions(
        boolean deep,
        int maxFiles,
        int maxDiffTokens,
        ModelTier forcedTier,
        int maxDebates,
        double routerThreshold,
        double maxCostUsd,
        int maxChunks,
        ModelProvider provider,
        DecisionProvider decisionModel) {

    public static ReviewOptions defaults() {
        return new ReviewOptions(false, 50, 60_000, null, 3, DecisionProvider.LOCAL.defaultRouterThreshold(), 2.0, 4,
                ModelProvider.ANTHROPIC, DecisionProvider.LOCAL);
    }
}
