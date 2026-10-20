package dev.langchain4j.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.prreviewer.PrReviewSystem;
import dev.langchain4j.prreviewer.agents.ToolBudget;
import dev.langchain4j.prreviewer.app.ReviewRequest;
import dev.langchain4j.prreviewer.app.ReviewService;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import dev.langchain4j.prreviewer.model.Category;
import dev.langchain4j.prreviewer.model.ReviewMode;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.Risk;
import dev.langchain4j.prreviewer.support.FixtureGitHubReads;
import dev.langchain4j.prreviewer.support.ScriptedChatModel;
import dev.langchain4j.prreviewer.support.TestData;
import dev.langchain4j.prreviewer.support.TestScenario;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * The tool box end to end, without a model: the scripted gatherer asks for a file until its budget is spent, the tool
 * bean answers from the fixtures through the build-time executors, and the budget wrapper counts the calls of the
 * invocation and refuses the thirteenth.
 */
@QuarkusTest
class GitHubToolsWiringTest {

    static final String HEAD = "c4c39e6187c1443a76c428fcf1bed5c2dccb5182";
    static final int BUDGET = 12;

    @Inject
    ReviewService reviews;

    @Inject
    PrReviewSystem system;

    @Inject
    TestScenario scenario;

    @Test
    void the_context_gatherer_reads_github_through_the_tool_box_within_its_budget() {
        Map<String, Object> responses = TestData.defaultResponses();
        responses.put("prTriage", TestData.summary(Category.BUGFIX, Risk.LOW, true));
        responses.put("contextGatherer", (Function<ScriptedChatModel.Call, Object>) call -> {
            List<String> results = call.toolResults();
            if (results.size() <= BUDGET) {
                return ToolExecutionRequest.builder()
                        .id("call-" + results.size())
                        .name("get_file_contents")
                        .arguments("{\"owner\":\"langchain4j\",\"repo\":\"langchain4j\",\"path\":\"README.md\",\"sha\":\"" + HEAD + "\"}")
                        .build();
            }
            return "Context from " + results.size() + " tool results";
        });
        FixtureGitHubReads github = new FixtureGitHubReads("pr6610");
        scenario.reset().responses(responses).github(github);

        reviews.review(new ReviewRequest(PrUrl.parse("langchain4j/langchain4j#6610"), ReviewMode.AUTO,
                ReviewOptions.defaults()), null, event -> {});

        List<ScriptedChatModel.Call> gatherer = scenario.calls().stream()
                .filter(call -> call.agent().equals("contextGatherer"))
                .toList();
        assertThat(gatherer).hasSize(BUDGET + 2); // one request per tool call, the refused one, then the answer
        assertThat(gatherer.get(0).request().toolSpecifications()).extracting(ToolSpecification::name)
                .containsExactlyInAnyOrderElementsOf(GitHubTools.TOOL_NAMES);

        List<String> results = gatherer.get(BUDGET + 1).toolResults();
        assertThat(results).hasSize(BUDGET + 1);
        assertThat(results.subList(0, BUDGET))
                .allMatch(text -> text.equals("Fixture content of README.md at " + HEAD + " in langchain4j/langchain4j"));
        assertThat(results.get(BUDGET)).isEqualTo(ToolBudget.EXHAUSTED);
        assertThat(github.calls().stream().filter(call -> call.startsWith("contents:")).count()).isEqualTo(BUDGET);

        String codeContext = ScopeState.read(
                system.agentMonitor().successfulExecutions().get(0).agenticScope(), Keys.CODE_CONTEXT);
        assertThat(codeContext).isEqualTo("Context from " + (BUDGET + 1) + " tool results");
    }
}
