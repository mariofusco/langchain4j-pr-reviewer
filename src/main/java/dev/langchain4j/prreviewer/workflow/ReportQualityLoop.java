package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.agents.ReportCritic;
import dev.langchain4j.prreviewer.agents.ReportWriter;
import dev.langchain4j.prreviewer.app.ActiveReview;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import dev.langchain4j.prreviewer.model.Critique;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.model.Scores;

/** Writes the report and rewrites it until the critic is satisfied; the bar is lowered after the first round. */
public interface ReportQualityLoop {

    double FIRST_ROUND_THRESHOLD = 0.85;
    double LATER_ROUNDS_THRESHOLD = 0.7;

    @LoopAgent(name = "reportQualityLoop", description = "Writes the report and refines it until the critic passes",
            outputKey = Keys.REVIEW_REPORT, maxIterations = 3,
            subAgents = {ReportWriter.class, ReportAssembler.class, ReportCritic.class})
    ReviewReport write(Scores scores);

    @ExitCondition(testExitAtLoopEnd = true, description = "the critic is satisfied, or the cost cap is reached")
    static boolean exit(AgenticScope scope, Critique reportCritique, ReviewOptions options) {
        // the round counter of reportAssembler, not @LoopCounter, which the Quarkus build-time validation rejects
        int round = ScopeState.read(scope, Keys.REPORT_ROUND);
        double threshold = round <= 1 ? FIRST_ROUND_THRESHOLD : LATER_ROUNDS_THRESHOLD;
        return reportCritique.score() >= threshold || ActiveReview.get().ledger().exceeded(options.maxCostUsd());
    }
}
