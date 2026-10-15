package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.K;
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
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import java.util.List;

/** Writes the prose of the report; findings, scores and verdict are rendered from the data by the assembler. */
public interface ReportWriter {

    @SystemMessage(fromResource = "/prompts/writer-system.md")
    @UserMessage(fromResource = "/prompts/report-writer-user.md")
    @Agent(name = "reportWriter", description = "Writes the prose of the review report",
            typedOutputKey = Keys.ReportDraft.class)
    ReportDraft write(@K(Keys.PrOverview.class) String overview,
                      @K(Keys.PrSummary.class) PrSummary summary,
                      @K(Keys.Usefulness.class) UsefulnessAssessment usefulness,
                      @K(Keys.ActiveFindings.class) List<Finding> findings,
                      @K(Keys.Scores.class) Scores scores,
                      @K(Keys.Verdict.class) Verdict verdict,
                      @K(Keys.VerdictReasoning.class) String verdictReasoning,
                      @K(Keys.Limitations.class) List<String> limitations,
                      @K(Keys.ReviewerGuidance.class) String guidance,
                      @K(Keys.ReportDraft.class) ReportDraft previousDraft,
                      @K(Keys.ReportCritique.class) Critique critique);

    @ChatModelSupplier
    static ChatModel model(AgenticScope scope) {
        return ModelSelection.select(scope, "reportWriter", ModelTier.STANDARD, ModelTier.PREMIUM,
                Escalation::previousDraftCriticized);
    }
}
