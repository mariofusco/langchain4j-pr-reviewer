package dev.langchain4j.prreviewer.workflow;

import static dev.langchain4j.prreviewer.support.TestData.raw;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.prreviewer.github.DiffParser;
import dev.langchain4j.prreviewer.model.FileChange;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.FindingCategory;
import dev.langchain4j.prreviewer.model.RawFinding;
import dev.langchain4j.prreviewer.model.Severity;
import dev.langchain4j.prreviewer.support.FixtureGitHubGateway;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConsolidationTest {

    static final List<FileChange> CHANGES = DiffParser.parse(FixtureGitHubGateway.diff6610());
    static final String MAIN = "langchain4j/src/main/java/dev/langchain4j/service/DefaultAiServices.java";

    @Test
    void discards_findings_outside_the_diff_and_resolves_short_paths() {
        Map<String, List<RawFinding>> byReviewer = Map.of("r", List.of(
                raw(FindingCategory.CORRECTNESS, Severity.HIGH, MAIN, 130, 0.9),
                raw(FindingCategory.CORRECTNESS, Severity.HIGH, "dev/langchain4j/service/DefaultAiServices.java", 185, 0.9),
                raw(FindingCategory.CORRECTNESS, Severity.HIGH, MAIN, 1000, 0.9),
                raw(FindingCategory.SECURITY, Severity.CRITICAL, "src/Invented.java", 1, 0.9),
                raw(FindingCategory.DESIGN, Severity.LOW, MAIN, 0, 0.5)));

        Consolidation.Result result = Consolidation.consolidate(byReviewer, CHANGES, 3);

        assertThat(result.discarded()).isEqualTo(2);
        assertThat(result.findings()).extracting(Finding::id).containsExactly("F1", "F2", "F3");
        assertThat(result.findings()).extracting(Finding::file).containsOnly(MAIN);
    }

    @Test
    void merges_duplicates_from_several_reviewers_keeping_the_most_severe() {
        Map<String, List<RawFinding>> byReviewer = new LinkedHashMap<>();
        byReviewer.put("correctnessReviewer", List.of(raw(FindingCategory.CORRECTNESS, Severity.MEDIUM, MAIN, 130, 0.9)));
        byReviewer.put("chunkReviewer", List.of(raw(FindingCategory.CORRECTNESS, Severity.HIGH, MAIN, 131, 0.95)));

        Consolidation.Result result = Consolidation.consolidate(byReviewer, CHANGES, 3);

        assertThat(result.findings()).singleElement().satisfies(f -> {
            assertThat(f.severity()).isEqualTo(Severity.HIGH);
            assertThat(f.reviewer()).isEqualTo("chunkReviewer+correctnessReviewer");
        });
    }

    @Test
    void adjacent_findings_of_one_reviewer_are_distinct_unless_identical() {
        Map<String, List<RawFinding>> byReviewer = Map.of("correctnessReviewer", List.of(
                raw(FindingCategory.CORRECTNESS, Severity.HIGH, MAIN, 130, 0.9),
                raw(FindingCategory.CORRECTNESS, Severity.MEDIUM, MAIN, 131, 0.9),
                raw(FindingCategory.CORRECTNESS, Severity.MEDIUM, MAIN, 131, 0.9)));

        Consolidation.Result result = Consolidation.consolidate(byReviewer, CHANGES, 3);

        assertThat(result.findings()).extracting(Finding::line).containsExactly(130, 131);
    }

    @Test
    void contests_doubtful_and_contradicted_high_severity_findings_up_to_the_limit() {
        Map<String, List<RawFinding>> byReviewer = new LinkedHashMap<>();
        byReviewer.put("correctnessReviewer", List.of(
                raw(FindingCategory.CORRECTNESS, Severity.CRITICAL, MAIN, 125, 0.5),
                raw(FindingCategory.CORRECTNESS, Severity.HIGH, MAIN, 140, 0.95),
                raw(FindingCategory.CORRECTNESS, Severity.HIGH, MAIN, 185, 0.6)));
        byReviewer.put("designReviewer", List.of(raw(FindingCategory.DESIGN, Severity.NIT, MAIN, 141, 0.9)));

        List<Finding> contested = Consolidation.consolidate(byReviewer, CHANGES, 2).contested();

        assertThat(contested).extracting(Finding::severity).containsExactly(Severity.CRITICAL, Severity.HIGH);
        assertThat(contested).extracting(Finding::line).containsExactly(125, 185);
        List<Finding> all = Consolidation.consolidate(byReviewer, CHANGES, 5).contested();
        // the confident HIGH at line 140 is contradicted by a NIT at the same place
        assertThat(all).extracting(Finding::line).containsExactlyInAnyOrder(125, 185, 140);
    }
}
