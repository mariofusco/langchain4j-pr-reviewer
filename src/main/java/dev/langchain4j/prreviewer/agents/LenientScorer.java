package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.ScoreCard;
import dev.langchain4j.prreviewer.model.UsefulnessAssessment;
import dev.langchain4j.prreviewer.models.ModelCatalog;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import java.util.List;

public interface LenientScorer {

    @SystemMessage(fromResource = "/prompts/scoring-rubric.md")
    @UserMessage(fromResource = "/prompts/lenient-scorer-user.md")
    @Agent(name = "lenientScorer", description = "Scores the pull request independently, as a pragmatic maintainer", outputKey = Keys.SCORE_VOTE3)
    ScoreCard score(String prOverview,
                    PrSummary prSummary,
                    UsefulnessAssessment usefulness,
                    List<Finding> activeFindings,
                    String reviewerGuidance);

    @ChatModelSupplier
    static ChatModel model(@CdiBean ModelCatalog catalog, AgenticScope scope) {
        return ModelSelection.fixed(catalog, scope, ModelTier.ECONOMY);
    }
}
