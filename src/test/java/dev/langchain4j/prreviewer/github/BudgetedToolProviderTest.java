package dev.langchain4j.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BudgetedToolProviderTest {

    static final ToolSpecification READ = ToolSpecification.builder().name("get_file_contents").build();
    static final ToolExecutionRequest CALL = ToolExecutionRequest.builder().name("get_file_contents").arguments("{}").build();

    final AtomicInteger serverCalls = new AtomicInteger();
    final AtomicInteger providerCreations = new AtomicInteger();

    BudgetedToolProvider budgeted(ToolExecutor executor, int budget) {
        return new BudgetedToolProvider(() -> {
            providerCreations.incrementAndGet();
            return request -> new ToolProviderResult(Map.of(READ, executor));
        }, budget);
    }

    @Test
    void each_invocation_gets_its_own_budget() {
        BudgetedToolProvider provider = budgeted((request, memoryId) -> "content " + serverCalls.incrementAndGet(), 2);

        ToolExecutor first = provider.provideTools(null).toolExecutorByName("get_file_contents");
        assertThat(first.execute(CALL, null)).isEqualTo("content 1");
        assertThat(first.execute(CALL, null)).isEqualTo("content 2");
        assertThat(first.execute(CALL, null)).isEqualTo(BudgetedToolProvider.EXHAUSTED);
        assertThat(serverCalls).hasValue(2);

        ToolExecutor second = provider.provideTools((ToolProviderRequest) null).toolExecutorByName("get_file_contents");
        assertThat(second.execute(CALL, null)).isEqualTo("content 3");
    }

    @Test
    void the_server_starts_when_tools_are_first_requested() {
        BudgetedToolProvider provider = budgeted((request, memoryId) -> "content", 2);
        assertThat(providerCreations).hasValue(0);

        provider.provideTools(null);
        provider.provideTools(null);

        assertThat(providerCreations).hasValue(1);
    }

    @Test
    void invalid_arguments_go_back_to_the_model() {
        BudgetedToolProvider provider = budgeted((request, memoryId) -> {
            throw new ToolArgumentsException("path is required");
        }, 2);

        String answer = provider.provideTools(null).toolExecutorByName("get_file_contents").execute(CALL, null);

        assertThat(answer).isEqualTo("Invalid arguments: path is required");
    }
}
