package dev.langchain4j.prreviewer.hitl;

import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.prreviewer.app.ActiveReview;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.observability.CostLedger;
import java.util.List;

/** Human in the loop: shows the report and asks to approve, edit, contest or drop findings, or reject. */
public interface HumanApproval {

    @HumanInTheLoop(name = "humanApproval",
            description = "Shows the report to the human reviewer and asks to approve, edit, contest or drop findings, or reject",
            outputKey = Keys.APPROVAL)
    static ApprovalDecision ask(ReviewReport reviewReport, List<Finding> findings, ReviewOptions options) {
        ActiveReview active = ActiveReview.get();
        CostLedger ledger = active.ledger();
        return active.console().askApproval(reviewReport, findings.stream().filter(Finding::active).toList(),
                ledger.total(), options.maxCostUsd(), !ledger.exceeded(options.maxCostUsd()));
    }
}
