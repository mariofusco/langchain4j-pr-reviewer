package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.prreviewer.github.GitHubTools;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutor;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Bounds the GitHub tool calls of one agent invocation. Quarkus runs every tool through its executors and lets wrapper
 * beans see each call with the invocation context of the AI service call: one agent invocation, with its whole tool
 * loop, has one invocation id, and the interface name is the agent's. Past the budget a tool answers that it is
 * exhausted instead of reading GitHub, and the model concludes with what it has. The round trips of an invocation are
 * bounded as well, by {@code quarkus.langchain4j.ai-service.max-tool-calling-round-trips}, set above the largest budget.
 */
@ApplicationScoped
@Unremovable
public class ToolBudget implements QuarkusToolExecutor.Wrapper {

    public static final String EXHAUSTED = "Tool budget exhausted: answer with what you have, without calling tools.";

    /** Calls per invocation of each tool-using agent; their prompts state the same numbers. */
    static final Map<String, Integer> BUDGETS = Map.of(
            ContextGatherer.class.getName(), 12,
            FindingProsecutor.class.getName(), 6,
            FindingDefender.class.getName(), 6);

    /** Invocations remembered; a review has a few dozen tool-using invocations at most. */
    private static final int MAX_TRACKED_INVOCATIONS = 512;

    private final Map<UUID, Integer> calls = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Integer> eldest) {
            return size() > MAX_TRACKED_INVOCATIONS;
        }
    };

    @Override
    public ToolExecutionResult wrap(ToolExecutionRequest request, InvocationContext context,
                                    BiFunction<ToolExecutionRequest, InvocationContext, ToolExecutionResult> execute,
                                    QuarkusToolExecutor executor) {
        Integer budget = context == null || context.interfaceName() == null || context.invocationId() == null
                || !GitHubTools.TOOL_NAMES.contains(request.name())
                ? null
                : BUDGETS.get(context.interfaceName());
        if (budget != null && count(context.invocationId()) > budget) {
            return ToolExecutionResult.builder().resultText(EXHAUSTED).isError(true).build();
        }
        return execute.apply(request, context);
    }

    private synchronized int count(UUID invocation) {
        return calls.merge(invocation, 1, Integer::sum);
    }
}
