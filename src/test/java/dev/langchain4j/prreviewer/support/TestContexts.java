package dev.langchain4j.prreviewer.support;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.prreviewer.AppContext;
import dev.langchain4j.prreviewer.github.GitHubGateway;
import dev.langchain4j.prreviewer.hitl.ReviewerConsole;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.models.ChatModels;
import dev.langchain4j.prreviewer.observability.CostLedger;
import java.nio.file.Path;

public final class TestContexts {

    /** A model that fails when called: orchestration tests replace every AI agent with a stub. */
    public static final ChatModels NO_MODELS = tier -> new ChatModel() {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            throw new AssertionError("Unexpected model call with stubbed agents");
        }
    };

    private TestContexts() {}

    public static AppContext install(GitHubGateway github, ChatModels models, DecisionModel decisionModel,
                                     ReviewerConsole console, ReviewOptions options, CostLedger ledger, Path outputDir) {
        AppContext context = AppContext.builder()
                .github(github)
                .models(models)
                .decisionModel(decisionModel)
                .console(console)
                .ledger(ledger)
                .options(options)
                .outputDir(outputDir)
                .build();
        AppContext.install(context);
        return context;
    }
}
