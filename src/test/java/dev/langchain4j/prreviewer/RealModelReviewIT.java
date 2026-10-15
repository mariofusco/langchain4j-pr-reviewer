package dev.langchain4j.prreviewer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.prreviewer.github.GitHubMcp;
import dev.langchain4j.prreviewer.model.MetricScore;
import dev.langchain4j.prreviewer.model.ReviewMode;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.models.ChatModelProvider;
import dev.langchain4j.prreviewer.models.DecisionModels;
import dev.langchain4j.prreviewer.observability.CostLedger;
import dev.langchain4j.prreviewer.observability.CostTrackingListener;
import dev.langchain4j.prreviewer.observability.DecisionCostListener;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

/**
 * A full automatic review of a small real pull request with real models (costs a few cents). Asserts structure and
 * consistency, not wording. Subclasses pick the provider.
 */
abstract class RealModelReviewIT {

    protected abstract ChatModelProvider provider();

    protected abstract double costCapUsd();

    @TempDir
    Path out;

    @AfterEach
    void uninstall() {
        AppContext.install(null);
    }

    @Test
    void reviews_a_small_pull_request() throws Exception {
        CostLedger ledger = new CostLedger();
        ReviewOptions options = new ReviewOptions(false, 50, 60_000, null, 3,
                DecisionModels.defaultRouterThreshold(DecisionModels.Provider.LOCAL), costCapUsd(), 4);
        try (McpClient client = GitHubMcp.client(GitHubMcp.Transport.LOCAL, GitHubMcp.resolveToken())) {
            AppContext.install(AppContext.builder()
                    .github(GitHubMcp.gateway(client))
                    .githubTools(GitHubMcp.readTools(client))
                    .models(provider().fromEnvironment(List.of(new CostTrackingListener(ledger))))
                    .decisionModel(DecisionModels.create(DecisionModels.Provider.LOCAL, new DecisionCostListener(ledger)))
                    .ledger(ledger)
                    .options(options)
                    .outputDir(out)
                    .build());
            PrReviewSystem system = PrReviewSystems.create();

            ReviewReport report = system.review("langchain4j/langchain4j#6600", ReviewMode.AUTO, options);
            HtmlReportGenerator.generateReport(system.agentMonitor(), out.resolve("trace.html"));

            assertThat(report.verdict()).isNotNull();
            assertThat(report.summary()).isNotBlank();
            assertThat(report.scores().metrics()).allSatisfy(m -> assertThat(m.score()).isBetween(0.0, 10.0));
            assertThat(report.scores().metrics()).filteredOn(MetricScore::applicable).isNotEmpty();
            assertThat(report.markdown()).contains("### What is good").contains("### What is wrong").contains("### Scores");
            assertThat(report.run().reviewersRun()).isNotEmpty();
            assertThat(ledger.total()).isPositive();
            assertThat(out.resolve("langchain4j-langchain4j-6600/report.json")).exists();
            System.out.println("===== " + provider() + " =====");
            System.out.println(report.markdown());
            System.out.println(ledger.summary());
        }
    }
}
