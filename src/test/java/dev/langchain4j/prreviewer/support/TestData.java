package dev.langchain4j.prreviewer.support;

import dev.langchain4j.prreviewer.model.Category;
import dev.langchain4j.prreviewer.model.Critique;
import dev.langchain4j.prreviewer.model.FindingCategory;
import dev.langchain4j.prreviewer.model.Metric;
import dev.langchain4j.prreviewer.model.MetricScore;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.RawFinding;
import dev.langchain4j.prreviewer.model.ReportDraft;
import dev.langchain4j.prreviewer.model.Risk;
import dev.langchain4j.prreviewer.model.ScoreCard;
import dev.langchain4j.prreviewer.model.Severity;
import dev.langchain4j.prreviewer.model.Size;
import dev.langchain4j.prreviewer.model.UsefulnessAssessment;
import dev.langchain4j.prreviewer.model.Verdict;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builders of model-facing records for tests. */
public final class TestData {

    public static final String MAIN_FILE = "langchain4j/src/main/java/dev/langchain4j/service/DefaultAiServices.java";
    public static final String VALIDATION_FILE = "langchain4j/src/main/java/dev/langchain4j/service/AiServiceValidation.java";

    private TestData() {}

    public static PrSummary summary(Category category, Risk risk, boolean needsContext) {
        return new PrSummary("Decide in one place which methods call the model", category, Size.MEDIUM, risk,
                List.of("langchain4j AI services"), needsContext, List.of());
    }

    public static RawFinding raw(FindingCategory category, Severity severity, String file, int line, double confidence) {
        return new RawFinding(category, severity, file, line, category + " issue at " + line,
                "Explanation of the " + category + " issue.", "Fix it.", "some code", confidence);
    }

    /** A response for every agent task: a MEDIUM correctness finding, a LOW design finding, a clear APPROVE card. */
    public static Map<String, Object> defaultResponses() {
        Map<String, Object> responses = new HashMap<>();
        responses.put("prTriage", summary(Category.REFACTOR, Risk.MEDIUM, false));
        responses.put("contextGatherer", "DefaultAiServices.build() is called by AiServices.build().");
        responses.put("questionGenerator", List.of("Should default methods ever call the model?"));
        responses.put("usefulnessAssessor", new UsefulnessAssessment(8.0, "Removes duplicated decisions.", false, List.of()));
        responses.put("correctnessReviewer", List.of(raw(FindingCategory.CORRECTNESS, Severity.MEDIUM, MAIN_FILE, 130, 0.9)));
        responses.put("securityReviewer", List.of());
        responses.put("designReviewer", List.of(raw(FindingCategory.DESIGN, Severity.LOW, VALIDATION_FILE, 60, 0.7)));
        responses.put("testReviewer", List.of());
        responses.put("docsApiReviewer", List.of());
        responses.put("correctnessReviewerFallback",
                List.of(raw(FindingCategory.CORRECTNESS, Severity.LOW, MAIN_FILE, 125, 0.6)));
        responses.put("securityReviewerFallback", List.of());
        responses.put("docsApiReviewerFallback", List.of());
        responses.put("chunkReviewer", List.of(raw(FindingCategory.CORRECTNESS, Severity.LOW, MAIN_FILE, 125, 0.6)));
        responses.put("findingProsecutor", "The null path is reachable. CONFIRMED");
        responses.put("findingDefender", "A guard upstream prevents it. REJECTED");
        responses.put("debateJudge", judge("CONFIRMED", "The prosecutor showed the path."));
        responses.put("scorer", card(8.5, Verdict.APPROVE));
        responses.put("neutralScorer", card(7.0, Verdict.COMMENT));
        responses.put("skepticalScorer", card(6.0, Verdict.COMMENT));
        responses.put("lenientScorer", card(8.0, Verdict.APPROVE));
        responses.put("reportWriter", new ReportDraft("A focused refactoring.", List.of("Single place for the decision."),
                List.of(), List.of("Add a changelog entry."), List.of()));
        responses.put("reportCritic", new Critique(0.9, List.of()));
        responses.put("reportFinalizer", new ReportDraft("A focused refactoring. (edited by the reviewer)",
                List.of("Single place for the decision."), List.of(), List.of("Add a changelog entry."), List.of()));
        return responses;
    }

    /** A judge's answer. */
    public static Map<String, Object> judge(String outcome, String reasoning) {
        Map<String, Object> verdict = new HashMap<>();
        verdict.put("outcome", outcome);
        verdict.put("reasoning", reasoning);
        return verdict;
    }

    public static ScoreCard card(double score, Verdict verdict) {
        List<MetricScore> metrics = new ArrayList<>();
        for (Metric metric : Metric.values()) {
            metrics.add(new MetricScore(metric, true, score, metric.label() + " rationale"));
        }
        return new ScoreCard(metrics, verdict, "Proposed " + verdict + ".", 0.9);
    }
}
