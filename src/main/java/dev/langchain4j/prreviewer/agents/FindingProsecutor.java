package dev.langchain4j.prreviewer.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.ToolProviderSupplier;
import dev.langchain4j.agentic.patterns.debate.DebatePlanner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.prreviewer.AppContext;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.models.ModelSelection;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.V;

/** A debater about a contested finding; its name labels its turns in the debate context. */
public interface FindingProsecutor {

    int MAX_TOOL_CALLS = 6;

    @SystemMessage(fromResource = "/prompts/debate-system.md")
    @UserMessage(fromResource = "/prompts/prosecutor-user.md")
    @Agent(name = "findingProsecutor", description = "Argues that a contested finding is real", typedOutputKey = Keys.Prosecution.class)
    String argue(@K(Keys.ContestedFinding.class) Finding finding,
                 @K(Keys.PrOverview.class) String overview,
                 @K(Keys.ContestedDiff.class) String diff,
                 @K(Keys.ReviewerGuidance.class) String guidance,
                 @V(DebatePlanner.DEBATE_CONTEXT_KEY) String debateContext);

    @ToolProviderSupplier
    static ToolProvider tools() {
        return AppContext.get().githubTools();
    }

    @ChatModelSupplier
    static ChatModel model(AgenticScope scope) {
        return ModelSelection.fixedForTools(scope, ModelTier.STANDARD);
    }
}
