package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.prreviewer.hitl.AutoApproval;
import dev.langchain4j.prreviewer.hitl.HumanApproval;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.ReviewMode;

/** The human approves in interactive mode; the report is approved automatically otherwise. */
public interface ApprovalGate {

    @ConditionalAgent(name = "approvalGate", description = "Gets the report approved",
            outputKey = Keys.APPROVAL, subAgents = {HumanApproval.class, AutoApproval.class})
    ApprovalDecision approve(ReviewMode mode);

    @ActivationCondition(value = HumanApproval.class, description = "interactive mode")
    static boolean interactive(ReviewMode mode) {
        return mode == ReviewMode.INTERACTIVE;
    }

    @ActivationCondition(value = AutoApproval.class, description = "automatic mode")
    static boolean automatic(ReviewMode mode) {
        return mode != ReviewMode.INTERACTIVE;
    }
}
