package dev.langchain4j.prreviewer.hitl;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import java.util.List;

/**
 * Non-AI agent opening each pass of the revision loop. On the first pass it does nothing; after a {@code contest}
 * answer it drops the dropped findings, queues the contested ones for a debate, passes the reviewer's notes on, and
 * clears the previous report so that it is written again.
 */
public class ApplyHumanFeedback {

    @Agent(name = "applyHumanFeedback", description = "Applies the human reviewer's contest and drop decisions",
            outputKey = Keys.FINDINGS)
    public static List<Finding> apply(AgenticScope scope, List<Finding> findings, ReviewOptions options) {
        ApprovalDecision approval = ScopeState.read(scope, Keys.APPROVAL);
        if (approval == null || approval.decision() != ApprovalDecision.Decision.CONTEST) {
            return findings;
        }
        int round = ScopeState.<Integer>read(scope, Keys.REVISION_ROUND) + 1;
        List<Finding> updated = HumanFeedback.applyDrops(findings, approval);
        List<Finding> contested = HumanFeedback.contested(updated, approval, options.maxDebates());
        if (contested.size() < approval.contestedFindingIds().size()) {
            ScopeState.append(scope, Keys.LIMITATIONS, "Only " + contested.size() + " of the "
                    + approval.contestedFindingIds().size() + " contested findings were debated (--max-debates "
                    + options.maxDebates() + ").");
        }
        scope.writeState(Keys.CONTESTED_FINDINGS, contested);

        String guidance = ScopeState.read(scope, Keys.REVIEWER_GUIDANCE);
        String note = HumanFeedback.guidanceNote(approval, round);
        scope.writeState(Keys.REVIEWER_GUIDANCE, guidance.isBlank() ? note : guidance + "\n" + note);
        scope.writeState(Keys.REVISION_ROUND, round);
        scope.writeState(Keys.REPORT_ROUND, 0);
        ScopeState.reset(scope, Keys.APPROVAL);
        ScopeState.reset(scope, Keys.REPORT_DRAFT);
        ScopeState.reset(scope, Keys.REPORT_CRITIQUE);
        return updated;
    }
}
