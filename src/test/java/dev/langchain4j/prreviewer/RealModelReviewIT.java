package dev.langchain4j.prreviewer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.prreviewer.app.ReviewOutcome;
import dev.langchain4j.prreviewer.app.ReviewRequest;
import dev.langchain4j.prreviewer.app.ReviewService;
import dev.langchain4j.prreviewer.github.PrUrl;
import dev.langchain4j.prreviewer.model.DecisionProvider;
import dev.langchain4j.prreviewer.model.MetricScore;
import dev.langchain4j.prreviewer.model.ModelProvider;
import dev.langchain4j.prreviewer.model.ReviewMode;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.ReviewReport;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * A full automatic review of a small real pull request with real models, the local decision model and the GitHub MCP
 * server (costs a few cents). Asserts structure and consistency, not wording. Subclasses pick the provider.
 */
abstract class RealModelReviewIT {

    @Inject
    ReviewService reviews;

    protected abstract ModelProvider provider();

    protected abstract double costCapUsd();

    @Test
    void reviews_a_small_pull_request() throws Exception {
        ReviewOptions options = new ReviewOptions(false, 50, 60_000, null, 3,
                DecisionProvider.LOCAL.defaultRouterThreshold(), costCapUsd(), 4, provider(), DecisionProvider.LOCAL);

        ReviewOutcome outcome = reviews.review(new ReviewRequest(PrUrl.parse("langchain4j/langchain4j#6600"),
                ReviewMode.AUTO, options), null, event -> {});
        ReviewReport report = outcome.report();

        assertThat(report.verdict()).isNotNull();
        assertThat(report.summary()).isNotBlank();
        assertThat(report.scores().metrics()).allSatisfy(m -> assertThat(m.score()).isBetween(0.0, 10.0));
        assertThat(report.scores().metrics()).filteredOn(MetricScore::applicable).isNotEmpty();
        assertThat(report.markdown()).contains("### What is good").contains("### What is wrong").contains("### Scores");
        assertThat(report.run().reviewersRun()).isNotEmpty();
        assertThat(outcome.ledger().total()).isPositive();
        assertThat(outcome.outputDir().resolve("report.json")).exists();
        assertThat(outcome.outputDir().resolve("trace.html")).exists();
        System.out.println("===== " + provider() + " =====");
        System.out.println(report.markdown());
        System.out.println(outcome.ledger().summary());
    }
}
