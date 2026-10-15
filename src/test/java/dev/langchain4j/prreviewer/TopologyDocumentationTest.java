package dev.langchain4j.prreviewer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.prreviewer.app.ReviewService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Renders the topology of the agentic system Quarkus builds; {@code -Dupdate.topology=true} refreshes the docs. */
@QuarkusTest
class TopologyDocumentationTest {

    @Inject
    ReviewService reviews;

    @Test
    void renders_the_topology() throws Exception {
        String html = HtmlReportGenerator.generateTopology(reviews.rootAgent());

        assertThat(html).contains("prReviewSystem").contains("reviewPanel").contains("findingDebate")
                .contains("scoreVoting").contains("reviewRevisionLoop").contains("humanApproval")
                .contains("diffText").contains("debateContext");
        Files.writeString(Path.of("target/topology.html"), html);
        if (Boolean.getBoolean("update.topology")) {
            Files.createDirectories(Path.of("docs"));
            Files.writeString(Path.of("docs/topology.html"), html);
        }
    }
}
