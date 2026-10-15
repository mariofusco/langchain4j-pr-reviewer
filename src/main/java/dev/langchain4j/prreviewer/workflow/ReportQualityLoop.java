package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.LoopCounter;
import dev.langchain4j.prreviewer.AppContext;
import dev.langchain4j.prreviewer.agents.ReportCritic;
import dev.langchain4j.prreviewer.agents.ReportWriter;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Critique;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.model.Scores;

/** Writes the report and rewrites it until the critic is satisfied; the bar is lowered after the first round. */
public interface ReportQualityLoop {

    double FIRST_ROUND_THRESHOLD = 0.85;
    double LATER_ROUNDS_THRESHOLD = 0.7;

    @LoopAgent(name = "reportQualityLoop", description = "Writes the report and refines it until the critic passes",
            typedOutputKey = Keys.ReviewReport.class, maxIterations = 3,
            subAgents = {ReportWriter.class, ReportAssembler.class, ReportCritic.class})
    ReviewReport write(@K(Keys.Scores.class) Scores scores);

    @ExitCondition(testExitAtLoopEnd = true, description = "the critic is satisfied, or the cost cap is reached")
    static boolean exit(@K(Keys.ReportCritique.class) Critique critique,
                        @K(Keys.Options.class) ReviewOptions options,
                        @LoopCounter int iteration) {
        double threshold = iteration <= 1 ? FIRST_ROUND_THRESHOLD : LATER_ROUNDS_THRESHOLD;
        return critique.score() >= threshold || AppContext.get().ledger().exceeded(options.maxCostUsd());
    }
}
