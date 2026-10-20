package dev.langchain4j.prreviewer.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.prreviewer.PrReviewSystem;
import dev.langchain4j.prreviewer.app.ReviewOutcome;
import dev.langchain4j.prreviewer.app.ReviewRequest;
import dev.langchain4j.prreviewer.app.ReviewService;
import dev.langchain4j.prreviewer.github.PrUrl;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import dev.langchain4j.prreviewer.model.Category;
import dev.langchain4j.prreviewer.model.DecisionProvider;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.ReviewMode;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.Risk;
import dev.langchain4j.prreviewer.model.Size;
import dev.langchain4j.prreviewer.support.FixtureGitHubReads;
import dev.langchain4j.prreviewer.support.TestData;
import dev.langchain4j.prreviewer.support.TestScenario;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The reviewer router with a real decision model and the real reviewer descriptions: together with the coverage floor,
 * typical changes reach the right reviewers. The chat models are scripted. Subclasses pick the decision model.
 */
abstract class AbstractReviewRouterIT {

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

    @Inject
    ReviewService reviews;

    @Inject
    PrReviewSystem system;

    @Inject
    TestScenario scenario;

    protected abstract DecisionProvider provider();

    /** The reviewers that ran: the router's choice and the coverage floor. */
    protected Set<String> route(String diff, PrSummary summary) {
        return Set.copyOf(run(diff, summary).report().run().reviewersRun());
    }

    /** The router's choice alone. */
    protected Set<String> routerChoice(String diff, PrSummary summary) {
        run(diff, summary);
        return panelOutputs().keySet();
    }

    private Map<String, Object> panelOutputs() {
        return ScopeState.read(system.agentMonitor().successfulExecutions().get(0).agenticScope(), Keys.PANEL_OUTPUTS);
    }

    private ReviewOutcome run(String diff, PrSummary summary) {
        Map<String, Object> responses = TestData.defaultResponses();
        responses.put("prTriage", summary);
        scenario.reset().responses(responses).github(new FixtureGitHubReads("pr6610", diff));
        ReviewOptions defaults = ReviewOptions.defaults();
        ReviewOptions options = new ReviewOptions(defaults.deep(), defaults.maxFiles(), defaults.maxDiffTokens(),
                defaults.forcedTier(), defaults.maxDebates(), provider().defaultRouterThreshold(), defaults.maxCostUsd(),
                defaults.maxChunks(), defaults.provider(), provider());
        ReviewOutcome outcome = reviews.review(new ReviewRequest(PrUrl.parse("langchain4j/langchain4j#6610"),
                ReviewMode.AUTO, options), null, event -> {});
        System.out.println(provider() + " " + summary.category() + " -> router " + panelOutputs().keySet()
                + ", ran " + outcome.report().run().reviewersRun());
        return outcome;
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
