package dev.langchain4j.prreviewer.hitl;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeLists;
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
            typedOutputKey = Keys.Findings.class)
    public static List<Finding> apply(AgenticScope scope,
                                      @K(Keys.Findings.class) List<Finding> findings,
                                      @K(Keys.Options.class) ReviewOptions options) {
        ApprovalDecision approval = scope.readState(Keys.Approval.class);
        if (approval == null || approval.decision() != ApprovalDecision.Decision.CONTEST) {
            return findings;
        }
        int round = scope.readState(Keys.RevisionRound.class) + 1;
        List<Finding> updated = HumanFeedback.applyDrops(findings, approval);
        List<Finding> contested = HumanFeedback.contested(updated, approval, options.maxDebates());
        if (contested.size() < approval.contestedFindingIds().size()) {
            ScopeLists.append(scope, Keys.Limitations.class, "Only " + contested.size() + " of the "
                    + approval.contestedFindingIds().size() + " contested findings were debated (--max-debates "
                    + options.maxDebates() + ").");
        }
        scope.writeState(Keys.ContestedFindings.class, contested);

        String guidance = scope.readState(Keys.ReviewerGuidance.class);
        String note = HumanFeedback.guidanceNote(approval, round);
        scope.writeState(Keys.ReviewerGuidance.class, guidance.isBlank() ? note : guidance + "\n" + note);
        scope.writeState(Keys.RevisionRound.class, round);
        scope.writeState(Keys.Approval.class, null);
        scope.writeState(Keys.ReportDraft.class, null);
        scope.writeState(Keys.ReportCritique.class, null);
        return updated;
    }
}
