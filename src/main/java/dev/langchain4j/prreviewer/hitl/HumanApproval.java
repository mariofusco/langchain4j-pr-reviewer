package dev.langchain4j.prreviewer.hitl;

import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.prreviewer.AppContext;
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
    static ApprovalDecision ask(@K(Keys.ReviewReport.class) ReviewReport report,
                                @K(Keys.Findings.class) List<Finding> findings,
                                @K(Keys.Options.class) ReviewOptions options) {
        AppContext context = AppContext.get();
        CostLedger ledger = context.ledger();
        return context.console().askApproval(report, findings.stream().filter(Finding::active).toList(),
                ledger.total(), options.maxCostUsd(), !ledger.exceeded(options.maxCostUsd()));
    }
}
