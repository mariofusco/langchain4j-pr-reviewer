package dev.langchain4j.prreviewer;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.prreviewer.github.GitHubGateway;
import dev.langchain4j.prreviewer.hitl.ReviewerConsole;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.models.ChatModels;
import dev.langchain4j.prreviewer.observability.CostLedger;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The runtime collaborators of the agents: GitHub access, models, the decision model, the console and the cost
 * ledger. Declarative agents are built from static methods, so they reach these through a process-wide context,
 * installed by the CLI before the agentic system is built and replaced by tests.
 */
public final class AppContext {

    private static volatile AppContext current;

    private final GitHubGateway github;
    private final ToolProvider githubTools;
    private final ChatModels models;
    private final DecisionModel decisionModel;
    private final ReviewerConsole console;
    private final CostLedger ledger;
    private final ReviewOptions options;
    private final Path outputDir;

    private AppContext(Builder builder) {
        this.github = Objects.requireNonNull(builder.github, "github");
        this.githubTools = builder.githubTools != null ? builder.githubTools : request -> ToolProviderResult.builder().build();
        this.models = Objects.requireNonNull(builder.models, "models");
        this.decisionModel = Objects.requireNonNull(builder.decisionModel, "decisionModel");
        this.console = builder.console;
        this.ledger = builder.ledger != null ? builder.ledger : new CostLedger();
        this.options = builder.options != null ? builder.options : ReviewOptions.defaults();
        this.outputDir = builder.outputDir != null ? builder.outputDir : Path.of("review-output");
    }

    public static AppContext get() {
        AppContext context = current;
        if (context == null) {
            throw new IllegalStateException("AppContext not installed");
        }
        return context;
    }

    /** Installs the context and returns the previous one. */
    public static AppContext install(AppContext context) {
        AppContext previous = current;
        current = context;
        return previous;
    }

    public GitHubGateway github() {
        return github;
    }

    /** The read-only GitHub tools offered to AI agents. */
    public ToolProvider githubTools() {
        return githubTools;
    }

    public ChatModels models() {
        return models;
    }

    public DecisionModel decisionModel() {
        return decisionModel;
    }

    public ReviewerConsole console() {
        if (console == null) {
            throw new IllegalStateException("No reviewer console: interactive mode is not available");
        }
        return console;
    }

    public CostLedger ledger() {
        return ledger;
    }

    /** The options the agentic system is built with (the reviewer router's threshold is fixed at build time). */
    public ReviewOptions options() {
        return options;
    }

    public Path outputDir() {
        return outputDir;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private GitHubGateway github;
        private ToolProvider githubTools;
        private ChatModels models;
        private DecisionModel decisionModel;
        private ReviewerConsole console;
        private CostLedger ledger;
        private ReviewOptions options;
        private Path outputDir;

        public Builder github(GitHubGateway github) {
            this.github = github;
            return this;
        }

        public Builder githubTools(ToolProvider githubTools) {
            this.githubTools = githubTools;
            return this;
        }

        public Builder models(ChatModels models) {
            this.models = models;
            return this;
        }

        public Builder decisionModel(DecisionModel decisionModel) {
            this.decisionModel = decisionModel;
            return this;
        }

        public Builder console(ReviewerConsole console) {
            this.console = console;
            return this;
        }

        public Builder ledger(CostLedger ledger) {
            this.ledger = ledger;
            return this;
        }

        public Builder options(ReviewOptions options) {
            this.options = options;
            return this;
        }

        public Builder outputDir(Path outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public AppContext build() {
            return new AppContext(this);
        }
    }
}
