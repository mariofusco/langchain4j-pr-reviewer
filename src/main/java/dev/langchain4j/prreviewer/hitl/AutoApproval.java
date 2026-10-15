package dev.langchain4j.prreviewer.hitl;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ApprovalDecision;

/** Non-AI agent: approves the report without asking, in automatic mode. */
public class AutoApproval {

    @Agent(name = "autoApproval", description = "Approves the report in automatic mode",
            typedOutputKey = Keys.Approval.class)
    public static ApprovalDecision approve() {
        return ApprovalDecision.approved();
    }
}
