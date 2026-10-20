package dev.langchain4j.prreviewer.support;

import dev.langchain4j.prreviewer.github.GitHubReads;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Set;

/**
 * What the test doubles answer in the current test: the scripted model responses per agent task, the agents whose
 * model call fails, the decision model and the GitHub fixtures. It starts with {@link TestData#defaultResponses()} and
 * pull request 6610, so tests that cannot reach it (command line tests) still get a complete review. The application is
 * shared by the tests of a profile, so every test starts with {@link #reset()}.
 */
@ApplicationScoped
public class TestScenario {

    private volatile Map<String, Object> responses = TestData.defaultResponses();
    private volatile Set<String> failingTasks = Set.of();
    private volatile FakeDecisionModel decisionModel = new FakeDecisionModel(Map.of());
    private volatile GitHubReads github = new FixtureGitHubReads("pr6610");
    private volatile java.util.List<ScriptedChatModel.Call> calls = ScriptedChatModel.newCallLog();

    public TestScenario reset() {
        responses = TestData.defaultResponses();
        failingTasks = Set.of();
        decisionModel = new FakeDecisionModel(Map.of());
        github = new FixtureGitHubReads("pr6610");
        calls = ScriptedChatModel.newCallLog();
        return this;
    }

    public TestScenario responses(Map<String, Object> responses) {
        this.responses = responses;
        return this;
    }

    /** The model calls of these agent tasks fail. */
    public TestScenario failing(String... tasks) {
        this.failingTasks = Set.of(tasks);
        return this;
    }

    public TestScenario decisionModel(FakeDecisionModel decisionModel) {
        this.decisionModel = decisionModel;
        return this;
    }

    public TestScenario github(GitHubReads github) {
        this.github = github;
        return this;
    }

    public Map<String, Object> responses() {
        return responses;
    }

    public Set<String> failingTasks() {
        return failingTasks;
    }

    public FakeDecisionModel decisionModel() {
        return decisionModel;
    }

    public GitHubReads github() {
        return github;
    }

    public java.util.List<ScriptedChatModel.Call> calls() {
        return calls;
    }
}
