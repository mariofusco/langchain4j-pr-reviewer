package dev.langchain4j.prreviewer;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgentConfigurator;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.prreviewer.agents.ContextGatherer;
import dev.langchain4j.prreviewer.agents.FindingDefender;
import dev.langchain4j.prreviewer.agents.FindingProsecutor;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import java.util.function.Function;

/** Builds the reviewer from its declarative definition. Build once per process; each review gets its own scope. */
public final class PrReviewSystems {

    private PrReviewSystems() {}

    public static PrReviewSystem create() {
        return create(null);
    }

    /**
     * @param subAgentResolver replaces sub-agents by class, for tests; null in production
     */
    public static PrReviewSystem create(Function<Class<?>, Object> subAgentResolver) {
        AgentConfigurator configurator = new AgentConfigurator(ctx -> {
            Class<?> type = ctx.agentServiceClass();
            if (type == ContextGatherer.class) {
                withToolPolicy(ctx.agentBuilder(), ContextGatherer.MAX_TOOL_CALLS);
            } else if (type == FindingProsecutor.class) {
                withToolPolicy(ctx.agentBuilder(), FindingProsecutor.MAX_TOOL_CALLS);
            } else if (type == FindingDefender.class) {
                withToolPolicy(ctx.agentBuilder(), FindingDefender.MAX_TOOL_CALLS);
            }
        }, subAgentResolver, null);
        return AgenticServices.createAgenticSystem(PrReviewSystem.class,
                AppContext.get().models().model(ModelTier.STANDARD), configurator);
    }

    /**
     * Bounded tool loops. Invalid tool arguments go back to the model so it can correct them; a failing tool reaches
     * the model only when its error is written for the model, like the GitHub MCP server's application errors
     * ("file not found"), and otherwise fails the agent, which the error handler then replaces by a fallback.
     */
    private static void withToolPolicy(AgentBuilder<?, ?> builder, int maxToolCalls) {
        builder.maxSequentialToolsInvocations(maxToolCalls)
                .toolArgumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm())
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm());
    }
}
