package dev.langchain4j.prreviewer.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.FindingCategory;
import dev.langchain4j.prreviewer.model.Severity;
import dev.langchain4j.prreviewer.model.Verdict;
import dev.langchain4j.prreviewer.model.Verification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvalMetricsTest {

    static Finding finding(String file, int line, Severity severity) {
        return new Finding("F1", "r", FindingCategory.CORRECTNESS, severity, file, line, "t", "e", "s", "x", 0.9,
                Verification.UNVERIFIED);
    }

    static EvalMetrics.Run run(Verdict verdict, double overall, List<Finding> findings) {
        return new EvalMetrics.Run(verdict, overall, Map.of(), findings, 0.1, 30);
    }

    static final EvalCase CASE = new EvalCase("o/r#1", "t", true, 2, 40, Verdict.COMMENT, List.of(
            new EvalCase.MustFind("src/main/java/a/A.java", 40, "null check"),
            new EvalCase.MustFind("src/main/java/a/B.java", 0, "naming")), "merged after comments");

    @Test
    void recall_matches_findings_by_file_and_nearby_line() {
        EvalMetrics.Run run = run(Verdict.COMMENT, 7, List.of(finding("src/main/java/a/A.java", 44, Severity.HIGH),
                finding("src/main/java/a/C.java", 1, Severity.MEDIUM)));

        assertThat(EvalMetrics.recall(CASE, run)).isEqualTo(0.5);
        assertThat(EvalMetrics.unmatchedRate(CASE, run)).isEqualTo(0.5);
        assertThat(EvalMetrics.recall(CASE, run(Verdict.APPROVE, 9, List.of(finding("a/B.java", 300, Severity.LOW)))))
                .isEqualTo(0.5);
        assertThat(EvalMetrics.unmatchedRate(CASE, run(Verdict.APPROVE, 9, List.of()))).isNull();
    }

    @Test
    void majority_verdict_and_stability() {
        assertThat(EvalMetrics.majorityVerdict(List.of(run(Verdict.APPROVE, 8, List.of()),
                run(Verdict.COMMENT, 7, List.of())))).isEqualTo(Verdict.COMMENT);
        assertThat(EvalMetrics.standardDeviation(List.of(6.0, 7.0, 8.0))).isCloseTo(1.0, within(1e-9));
        assertThat(EvalMetrics.mean(java.util.Arrays.asList(1.0, null, 3.0))).isEqualTo(2.0);
    }
}
