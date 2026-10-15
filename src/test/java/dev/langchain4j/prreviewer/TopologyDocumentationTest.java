package dev.langchain4j.prreviewer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.observability.CostLedger;
import dev.langchain4j.prreviewer.support.FakeDecisionModel;
import dev.langchain4j.prreviewer.support.FixtureGitHubGateway;
import dev.langchain4j.prreviewer.support.TestContexts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Renders the structure of the agentic system. With {@code -Dupdate.topology=true} it also refreshes the committed
 * {@code docs/topology.html}.
 */
class TopologyDocumentationTest {

    @AfterEach
    void uninstall() {
        AppContext.install(null);
    }

    @Test
    void renders_the_topology() throws Exception {
        TestContexts.install(new FixtureGitHubGateway("pr6610"), TestContexts.NO_MODELS, new FakeDecisionModel(Map.of()),
                null, ReviewOptions.defaults(), new CostLedger(), Path.of("target"));

        String html = HtmlReportGenerator.generateTopology(PrReviewSystems.create());

        assertThat(html).contains("prReviewSystem").contains("reviewPanel").contains("findingDebate")
                .contains("scoreVoting").contains("reviewRevisionLoop").contains("humanApproval");
        Files.writeString(Path.of("target/topology.html"), html);
        if (Boolean.getBoolean("update.topology")) {
            Files.createDirectories(Path.of("docs"));
            Files.writeString(Path.of("docs/topology.html"), html);
        }
    }
}
