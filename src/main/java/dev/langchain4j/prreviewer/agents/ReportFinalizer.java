package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.ReportDraft;
import dev.langchain4j.prreviewer.models.ModelCatalog;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import java.util.List;

public interface ReportFinalizer {

    @SystemMessage(fromResource = "/prompts/writer-system.md")
    @UserMessage(fromResource = "/prompts/report-finalizer-user.md")
    @Agent(name = "reportFinalizer", description = "Applies the human reviewer's edits to the report",
            outputKey = Keys.REPORT_DRAFT)
    ReportDraft finalizeDraft(ApprovalDecision approval, ReportDraft reportDraft, List<Finding> activeFindings);

    @ChatModelSupplier
    static ChatModel model(@CdiBean ModelCatalog catalog, AgenticScope scope) {
        return ModelSelection.fixed(catalog, scope, ModelTier.ECONOMY);
    }
}
