package dev.langchain4j.prreviewer.github;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Bounds the tool calls of one agent invocation. langchain4j asks a tool provider for its tools at every invocation,
 * so each invocation gets executors sharing a fresh counter. Past the budget a tool answers that it is exhausted
 * instead of calling the server, so the model concludes with what it has. Invalid arguments are returned to the model
 * so it can correct them; other execution errors reach the model as text, which is langchain4j's default.
 */
public final class BudgetedToolProvider implements ToolProvider {

    static final String EXHAUSTED = "Tool budget exhausted: answer with what you have, without calling tools.";

    private final Supplier<ToolProvider> delegateSupplier;
    private final int budget;
    private volatile ToolProvider delegate;

    public BudgetedToolProvider(Supplier<ToolProvider> delegateSupplier, int budget) {
        this.delegateSupplier = delegateSupplier;
        this.budget = budget;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolProviderResult tools = delegate().provideTools(request);
        AtomicInteger calls = new AtomicInteger();
        Map<ToolSpecification, ToolExecutor> budgeted = new LinkedHashMap<>();
        tools.tools().forEach((specification, executor) -> budgeted.put(specification, new Budgeted(executor, calls)));
        return new ToolProviderResult(budgeted);
    }

    private ToolProvider delegate() {
        ToolProvider current = delegate;
        if (current == null) {
            synchronized (this) {
                current = delegate;
                if (current == null) {
                    current = delegateSupplier.get();
                    delegate = current;
                }
            }
        }
        return current;
    }

    private final class Budgeted implements ToolExecutor {

        private final ToolExecutor executor;
        private final AtomicInteger calls;

        Budgeted(ToolExecutor executor, AtomicInteger calls) {
            this.executor = executor;
            this.calls = calls;
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            if (calls.incrementAndGet() > budget) {
                return EXHAUSTED;
            }
            try {
                return executor.execute(request, memoryId);
            } catch (ToolArgumentsException e) {
                return "Invalid arguments: " + e.getMessage();
            }
        }

        @Override
        public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
            if (calls.incrementAndGet() > budget) {
                return ToolExecutionResult.builder().resultText(EXHAUSTED).isError(true).build();
            }
            try {
                return executor.executeWithContext(request, context);
            } catch (ToolArgumentsException e) {
                return ToolExecutionResult.builder().resultText("Invalid arguments: " + e.getMessage()).isError(true).build();
            }
        }
    }
}
