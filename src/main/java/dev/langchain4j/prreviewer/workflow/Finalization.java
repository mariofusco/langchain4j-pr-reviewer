package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.prreviewer.agents.ReportFinalizer;
import dev.langchain4j.prreviewer.hitl.ApplyFinalFeedback;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.scoring.ScoreCalibrator;

/** Applies the last decision to the findings, recalibrates, applies the edits to the prose and renders again. */
public interface Finalization {

    @SequenceAgent(name = "finalization", description = "Applies the human reviewer's final edits",
            typedOutputKey = Keys.ReviewReport.class,
            subAgents = {ApplyFinalFeedback.class, ScoreCalibrator.class, ReportFinalizer.class, ReportAssembler.class})
    ReviewReport finalizeReport(@K(Keys.Approval.class) ApprovalDecision approval);
}
