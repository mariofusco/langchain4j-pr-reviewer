package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Critique;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.ReportDraft;
import dev.langchain4j.prreviewer.model.Scores;
import dev.langchain4j.prreviewer.model.UsefulnessAssessment;
import dev.langchain4j.prreviewer.model.Verdict;
import dev.langchain4j.prreviewer.models.ModelCatalog;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import java.util.List;

/** Writes the prose of the report; findings, scores and verdict are rendered from the data by the assembler. */
public interface ReportWriter {

    @SystemMessage(fromResource = "/prompts/writer-system.md")
    @UserMessage(fromResource = "/prompts/report-writer-user.md")
    @Agent(name = "reportWriter", description = "Writes the prose of the review report",
            outputKey = Keys.REPORT_DRAFT)
    ReportDraft write(String prOverview,
                      PrSummary prSummary,
                      UsefulnessAssessment usefulness,
                      List<Finding> activeFindings,
                      Scores scores,
                      Verdict verdict,
                      String verdictReasoning,
                      List<String> limitations,
                      String reviewerGuidance,
                      ReportDraft reportDraft,
                      Critique reportCritique);

    @ChatModelSupplier
    static ChatModel model(@CdiBean ModelCatalog catalog, AgenticScope scope) {
        return ModelSelection.select(catalog, scope, "reportWriter", ModelTier.STANDARD, ModelTier.PREMIUM,
                Escalation::previousDraftCriticized);
    }
}
