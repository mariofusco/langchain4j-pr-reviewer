package dev.langchain4j.prreviewer.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.prreviewer.model.CiStatus;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.FindingCategory;
import dev.langchain4j.prreviewer.model.PullRequest;
import dev.langchain4j.prreviewer.model.ReportDraft;
import dev.langchain4j.prreviewer.model.ReviewMode;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.model.RunInfo;
import dev.langchain4j.prreviewer.model.Scores;
import dev.langchain4j.prreviewer.model.Severity;
import dev.langchain4j.prreviewer.model.Verdict;
import dev.langchain4j.prreviewer.model.Verification;
import dev.langchain4j.prreviewer.scoring.Caps;
import dev.langchain4j.prreviewer.support.TestData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportRendererTest {

    static final PullRequest PR = new PullRequest("o", "r", 7, "Fix | the parser", "", "a", "main", "b", "sha", "",
            List.of(), List.of(), null, CiStatus.unknown(), List.of(), "");

    static Finding finding(String id, Severity severity, Verification verification) {
        return new Finding(id, "correctnessReviewer", FindingCategory.CORRECTNESS, severity, "src/A.java", 12,
                "Title " + id, "Explanation\nover two lines.", "Do this.", "x", 0.9, verification);
    }

    @Test
    void lists_every_significant_finding_even_when_the_writer_omits_it() {
        List<Finding> active = List.of(finding("F1", Severity.HIGH, Verification.CONFIRMED),
                finding("F2", Severity.MEDIUM, Verification.UNSURE), finding("F3", Severity.LOW, Verification.UNVERIFIED));
        ReportDraft draft = new ReportDraft("Summary.", List.of("Good."), List.of("F2", "F9"), List.of("Follow up."),
                List.of("Writer limitation."));
        Scores scores = new Scores(Caps.apply(TestData.card(6.0, Verdict.COMMENT).metrics(), List.of(), null, null).metrics(),
                6.0, 0.8);
        RunInfo run = new RunInfo(ReviewMode.AUTO, List.of("correctnessReviewer"), Map.of("correctnessReviewer",
                "claude-sonnet-5-5"), 0.42, 0);

        ReviewReport report = ReportRenderer.render(PR, draft, active, scores, Verdict.REQUEST_CHANGES,
                "Because of F1.", List.of("Code limitation.", "Writer limitation."), run);

        assertThat(report.wrong()).extracting(Finding::id).containsExactly("F2", "F1");
        assertThat(report.improvements()).containsExactly("Follow up.",
                "Minor: **F3 [LOW][CORRECTNESS]** `src/A.java:12` - Title F3. Explanation over two lines. _Suggestion:_ Do this.");
        assertThat(report.markdown())
                .contains("## Review of o/r#7: Fix | the parser")
                .contains("**Verdict:** REQUEST_CHANGES · **Overall:** 6.0/10 · **Confidence:** 0.8")
                .contains("**F1 [HIGH][CORRECTNESS]** `src/A.java:12` - Title F1 (confirmed by debate).")
                .contains("**F2 [MEDIUM][CORRECTNESS]** `src/A.java:12` - Title F2 (unconfirmed).")
                .contains("| Correctness | 6.0 | Correctness rationale |")
                .contains("Cost: $0.42")
                .contains("- correctnessReviewer: claude-sonnet-5-5")
                .doesNotContain("F9");
    }
}
