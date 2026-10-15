package dev.langchain4j.prreviewer;

import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.BeforeCall;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.agents.PrTriage;
import dev.langchain4j.prreviewer.agents.UsefulnessAssessor;
import dev.langchain4j.prreviewer.github.DiffPreprocessor;
import dev.langchain4j.prreviewer.github.PrFetcher;
import dev.langchain4j.prreviewer.github.RoutingInputs;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import dev.langchain4j.prreviewer.model.ReviewMode;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.workflow.ClarificationGate;
import dev.langchain4j.prreviewer.workflow.ContextGate;
import dev.langchain4j.prreviewer.workflow.CoverageFloor;
import dev.langchain4j.prreviewer.workflow.DiffChunkDeclaration;
import dev.langchain4j.prreviewer.workflow.DiffChunksProjection;
import dev.langchain4j.prreviewer.workflow.DiffTextProjection;
import dev.langchain4j.prreviewer.workflow.FinalizerGate;
import dev.langchain4j.prreviewer.workflow.FindingsConsolidator;
import dev.langchain4j.prreviewer.workflow.LargeDiffGate;
import dev.langchain4j.prreviewer.workflow.PrOverviewProjection;
import dev.langchain4j.prreviewer.workflow.ReportExporter;
import dev.langchain4j.prreviewer.workflow.ReviewBundleBuilder;
import dev.langchain4j.prreviewer.workflow.ReviewPanel;
import dev.langchain4j.prreviewer.workflow.ReviewRevisionLoop;

/**
 * The pull request reviewer: a deterministic sequence; model judgment lives inside its steps. Extends
 * {@link MonitoredAgent}, so every run is recorded by an agent monitor for the HTML trace.
 */
public interface PrReviewSystem extends MonitoredAgent {

    @SequenceAgent(name = "prReviewSystem",
            description = "Reviews a GitHub pull request and produces a scored report",
            outputKey = Keys.FINAL_REPORT,
            subAgents = {
                PrFetcher.class,
                DiffPreprocessor.class,
                DiffTextProjection.class,
                PrOverviewProjection.class,
                DiffChunksProjection.class,
                PrTriage.class,
                RoutingInputs.class,
                ContextGate.class,
                ClarificationGate.class,
                ReviewBundleBuilder.class,
                UsefulnessAssessor.class,
                ReviewPanel.class,
                CoverageFloor.class,
                DiffChunkDeclaration.class,
                LargeDiffGate.class,
                FindingsConsolidator.class,
                ReviewRevisionLoop.class,
                FinalizerGate.class,
                ReportExporter.class
            })
    ReviewReport review(String prUrl, ReviewMode mode, ReviewOptions options);

    /** The scope, and so this handler, is shared by the whole tree: it sees the failures of every agent. */
    @ErrorHandler
    static ErrorRecoveryResult onError(ErrorContext context) {
        return RootErrorHandler.recover(context);
    }

    /** Seeds the keys that some step may legitimately leave unset, so that no reader misses an argument. */
    @BeforeCall
    static void seedDefaults(AgenticScope scope) {
        ScopeState.seedDefaults(scope);
    }
}
