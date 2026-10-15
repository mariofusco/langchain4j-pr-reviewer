package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.prreviewer.agents.Scorer;
import dev.langchain4j.prreviewer.hitl.ApplyHumanFeedback;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.scoring.ScoreCalibrator;
import java.util.List;

/**
 * Debates, scores, writes and asks for approval; when the human reviewer contests or drops findings, it runs again
 * with the contested findings queued for a debate. At most two contest rounds.
 */
public interface ReviewRevisionLoop {

    @LoopAgent(name = "reviewRevisionLoop", description = "Debates, scores, writes and seeks approval until approved",
            typedOutputKey = Keys.ReviewReport.class, maxIterations = 3,
            subAgents = {ApplyHumanFeedback.class, DebateGate.class, DebateBudgetGuard.class,
                    ActiveFindingsSelector.class, Scorer.class, VotingGate.class, ScoreCalibrator.class,
                    ReportQualityLoop.class, ApprovalGate.class})
    ReviewReport revise(@K(Keys.Findings.class) List<Finding> findings);

    @ExitCondition(testExitAtLoopEnd = true, description = "the reviewer did not contest the report")
    static boolean exit(@K(Keys.Approval.class) ApprovalDecision approval) {
        return approval.decision() != ApprovalDecision.Decision.CONTEST;
    }
}
