package dev.langchain4j.prreviewer.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.prreviewer.AppContext;
import dev.langchain4j.prreviewer.PrReviewSystem;
import dev.langchain4j.prreviewer.PrReviewSystems;
import dev.langchain4j.prreviewer.agents.PrTriage;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Category;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.ReviewMode;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.Risk;
import dev.langchain4j.prreviewer.model.Size;
import dev.langchain4j.prreviewer.models.DecisionModels;
import dev.langchain4j.prreviewer.observability.CostLedger;
import dev.langchain4j.prreviewer.observability.DecisionCostListener;
import dev.langchain4j.prreviewer.support.FixtureGitHubGateway;
import dev.langchain4j.prreviewer.support.StubAgents;
import dev.langchain4j.prreviewer.support.TestContexts;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

/**
 * The reviewer router with the real local decision model (Ollama, {@code tev1:0.8b}) and the real reviewer
 * descriptions: together with the coverage floor, typical changes reach the right reviewers. The reviewers are stubs.
 * The probabilities of the small local model are printed; see {@link HostedReviewRouterIT} for the router alone.
 */
@EnabledIf("dev.langchain4j.prreviewer.support.Availability#localDecisionModel")
class ReviewRouterIT {

    static final String SQL_FIX = """
            diff --git a/core/src/main/java/com/acme/users/UserRepository.java b/core/src/main/java/com/acme/users/UserRepository.java
            --- a/core/src/main/java/com/acme/users/UserRepository.java
            +++ b/core/src/main/java/com/acme/users/UserRepository.java
            @@ -10,6 +10,8 @@ public class UserRepository {
                 public User findByName(String name) throws SQLException {
            -        return null;
            +        Statement statement = connection.createStatement();
            +        ResultSet rs = statement.executeQuery("select * from users where name = '" + name + "'");
            +        return rs.next() ? map(rs) : null;
                 }
             }
            """;

    static final String DOCS = """
            diff --git a/README.md b/README.md
            --- a/README.md
            +++ b/README.md
            @@ -1,3 +1,4 @@
             # Acme
            -Install with Maven.
            +Install with Maven or Gradle.
            +See the user guide for configuration.
            """;

    @TempDir
    Path out;

    @AfterEach
    void uninstall() {
        AppContext.install(null);
    }

    protected DecisionModels.Provider provider() {
        return DecisionModels.Provider.LOCAL;
    }

    /** The reviewers that ran: the router's choice and the coverage floor. */
    protected Set<String> route(String diff, PrSummary summary) {
        return run(diff, summary).run().reviewersRun().stream().collect(java.util.stream.Collectors.toSet());
    }

    /** The router's choice alone. */
    protected Set<String> routerChoice(String diff, PrSummary summary) {
        run(diff, summary);
        return lastScope.readState(Keys.PanelOutputs.class).keySet();
    }

    dev.langchain4j.agentic.scope.AgenticScope lastScope;

    private dev.langchain4j.prreviewer.model.ReviewReport run(String diff, PrSummary summary) {
        StubAgents stubs = StubAgents.defaults().returning(PrTriage.class, scope -> summary);
        DecisionModel decision = DecisionModels.create(provider(), new DecisionCostListener(new CostLedger()));
        ReviewOptions defaults = ReviewOptions.defaults();
        ReviewOptions options = new ReviewOptions(defaults.deep(), defaults.maxFiles(), defaults.maxDiffTokens(),
                defaults.forcedTier(), defaults.maxDebates(), DecisionModels.defaultRouterThreshold(provider()),
                defaults.maxCostUsd(), defaults.maxChunks());
        TestContexts.install(new FixtureGitHubGateway("pr6610", diff), TestContexts.NO_MODELS, decision, null,
                options, new CostLedger(), out);
        PrReviewSystem system = PrReviewSystems.create(stubs.resolver());
        var report = system.review("langchain4j/langchain4j#6610", ReviewMode.AUTO, options);
        lastScope = system.agentMonitor().successfulExecutions().get(0).agenticScope();
        System.out.println(provider() + " " + summary.category() + " -> router "
                + lastScope.readState(Keys.PanelOutputs.class).keySet() + ", ran " + report.run().reviewersRun());
        return report;
    }

    @Test
    void refactoring_of_production_code_gets_a_correctness_review() {
        Set<String> reviewers = route(null, new PrSummary("Decide in one place which AI service methods call the model",
                Category.REFACTOR, Size.MEDIUM, Risk.MEDIUM, List.of("AI services"), false, List.of()));

        assertThat(reviewers).containsAnyOf("correctnessReviewer", "correctnessReviewerFallback");
    }

    @Test
    void sql_built_from_input_gets_a_security_review() {
        Set<String> reviewers = route(SQL_FIX, new PrSummary("Implement user lookup by name", Category.BUGFIX,
                Size.SMALL, Risk.HIGH, List.of("user repository", "SQL"), false, List.of()));

        assertThat(reviewers).containsAnyOf("securityReviewer", "securityReviewerFallback");
    }

    @Test
    void documentation_change_gets_a_documentation_review_only() {
        Set<String> reviewers = route(DOCS, new PrSummary("Document Gradle installation", Category.DOCS, Size.SMALL,
                Risk.LOW, List.of("README"), false, List.of()));

        assertThat(reviewers).containsAnyOf("docsApiReviewer", "docsApiReviewerFallback")
                .doesNotContain("securityReviewer", "correctnessReviewer", "correctnessReviewerFallback");
    }
}
