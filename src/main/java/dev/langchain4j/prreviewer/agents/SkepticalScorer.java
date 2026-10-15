package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.ScoreCard;
import dev.langchain4j.prreviewer.model.UsefulnessAssessment;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import java.util.List;

public interface SkepticalScorer {

    @SystemMessage(fromResource = "/prompts/scoring-rubric.md")
    @UserMessage(fromResource = "/prompts/skeptical-scorer-user.md")
    @Agent(name = "skepticalScorer", description = "Scores the pull request independently, as a strict maintainer", typedOutputKey = Keys.ScoreVote2.class)
    ScoreCard score(@K(Keys.PrOverview.class) String overview,
                    @K(Keys.PrSummary.class) PrSummary summary,
                    @K(Keys.Usefulness.class) UsefulnessAssessment usefulness,
                    @K(Keys.ActiveFindings.class) List<Finding> findings,
                    @K(Keys.ReviewerGuidance.class) String guidance);

    @ChatModelSupplier
    static ChatModel model(AgenticScope scope) {
        return ModelSelection.fixed(scope, ModelTier.STANDARD);
    }
}
