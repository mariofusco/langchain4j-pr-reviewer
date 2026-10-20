package dev.langchain4j.prreviewer.agents;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ToolBudgetTest {

    static final ToolExecutionRequest READ = ToolExecutionRequest.builder().name("get_file_contents").arguments("{}").build();
    static final ToolExecutionRequest OTHER = ToolExecutionRequest.builder().name("calculator").arguments("{}").build();

    final ToolBudget budget = new ToolBudget();
    final AtomicInteger executions = new AtomicInteger();
    final BiFunction<ToolExecutionRequest, InvocationContext, ToolExecutionResult> execute =
            (request, context) -> ToolExecutionResult.builder().resultText("content " + executions.incrementAndGet()).build();

    @Test
    void each_invocation_gets_its_own_budget() {
        InvocationContext first = invocation(FindingDefender.class);
        IntStream.range(0, 6).forEach(i -> assertThat(call(first).resultText()).startsWith("content"));

        ToolExecutionResult refused = call(first);

        assertThat(refused.resultText()).isEqualTo(ToolBudget.EXHAUSTED);
        assertThat(refused.isError()).isTrue();
        assertThat(executions).hasValue(6);
        assertThat(call(invocation(FindingDefender.class)).resultText()).isEqualTo("content 7");
    }

    @Test
    void budgets_depend_on_the_agent() {
        InvocationContext gatherer = invocation(ContextGatherer.class);
        IntStream.range(0, 12).forEach(i -> assertThat(call(gatherer).resultText()).startsWith("content"));

        assertThat(call(gatherer).resultText()).isEqualTo(ToolBudget.EXHAUSTED);
    }

    @Test
    void other_tools_and_other_agents_pass_through() {
        InvocationContext gatherer = invocation(ContextGatherer.class);
        IntStream.range(0, 30).forEach(i -> assertThat(budget.wrap(OTHER, gatherer, execute, null).resultText()).startsWith("content"));

        InvocationContext other = invocation(PrTriage.class);
        IntStream.range(0, 30).forEach(i -> assertThat(call(other).resultText()).startsWith("content"));

        InvocationContext anonymous = InvocationContext.builder().build();
        IntStream.range(0, 30).forEach(i -> assertThat(call(anonymous).resultText()).startsWith("content"));
        assertThat(executions).hasValue(90);
    }

    private ToolExecutionResult call(InvocationContext context) {
        return budget.wrap(READ, context, execute, null);
    }

    private static InvocationContext invocation(Class<?> agent) {
        return InvocationContext.builder().invocationId(UUID.randomUUID()).interfaceName(agent.getName()).build();
    }
}
