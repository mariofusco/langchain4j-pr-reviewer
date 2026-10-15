package dev.langchain4j.prreviewer.cli;

import dev.langchain4j.prreviewer.app.ReviewOutcome;
import dev.langchain4j.prreviewer.app.ReviewRequest;
import dev.langchain4j.prreviewer.app.ReviewService;
import dev.langchain4j.prreviewer.app.RunEvent;
import dev.langchain4j.prreviewer.github.GitHubMcp;
import dev.langchain4j.prreviewer.github.PrUrl;
import dev.langchain4j.prreviewer.hitl.StdinReviewerConsole;
import dev.langchain4j.prreviewer.model.DecisionProvider;
import dev.langchain4j.prreviewer.model.MetricScore;
import dev.langchain4j.prreviewer.model.ModelProvider;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.PrRef;
import dev.langchain4j.prreviewer.model.ReviewMode;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.models.DecisionModels;
import dev.langchain4j.prreviewer.models.ModelCatalog;
import jakarta.inject.Inject;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "review", mixinStandardHelpOptions = true, sortOptions = false,
        description = "Reviews a GitHub pull request. Writes report.md, report.json and trace.html; never writes to GitHub.",
        footer = {"%nExamples:",
                "  pr-reviewer review https://github.com/owner/repo/pull/123",
                "  pr-reviewer review owner/repo#123 --mode interactive --provider openai"})
public class ReviewCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "PR", description = "Pull request URL or owner/repo#number")
    String pr;

    @ArgGroup(validate = false, heading = "%nReview%n", order = 1)
    Review review = new Review();

    @ArgGroup(validate = false, heading = "%nModels%n", order = 2)
    Models models = new Models();

    @ArgGroup(validate = false, heading = "%nBudgets%n", order = 3)
    Budgets budgets = new Budgets();

    @ArgGroup(validate = false, heading = "%nGitHub%n", order = 4)
    GitHub github = new GitHub();

    static class Review {
        @Option(names = "--mode", description = "AUTO or INTERACTIVE (default: ${DEFAULT-VALUE})")
        ReviewMode mode = ReviewMode.AUTO;

        @Option(names = "--deep", description = "Escalate correctness and security reviews to the large model")
        boolean deep;

        @Option(names = "--out", description = "Output directory (default: ${DEFAULT-VALUE})")
        Path out = Path.of("review-output");

        @Option(names = "--approve-on-eof", description = "In interactive mode, approve when the input ends")
        boolean approveOnEof;

        @Option(names = {"-q", "--quiet"}, description = "Print only the summary, not the progress of every agent")
        boolean quiet;
    }

    static class Models {
        @Option(names = "--provider", description = "ANTHROPIC or OPENAI (default: ${DEFAULT-VALUE})")
        ModelProvider provider = ModelProvider.ANTHROPIC;

        @Option(names = "--model-tier", description = "Force one tier for every agent: ECONOMY, STANDARD or PREMIUM")
        ModelTier modelTier;

        @Option(names = "--decision-model",
                description = "Reviewer router: LOCAL (System One API, Ollama), TYPESAFE or OPENROUTER (default: ${DEFAULT-VALUE})")
        DecisionProvider decisionModel = DecisionProvider.LOCAL;

        @Option(names = "--router-threshold",
                description = "Probability from which the router runs a reviewer (default: 0.2 for LOCAL, 0.5 otherwise)")
        Double routerThreshold;
    }

    static class Budgets {
        @Option(names = "--max-cost",
                description = "Soft cost cap in USD: past it voting, debates and escalation are skipped (default: ${DEFAULT-VALUE})")
        double maxCost = 2.0;

        @Option(names = "--max-debates", description = "Contested findings debated per revision (default: ${DEFAULT-VALUE})")
        int maxDebates = 3;

        @Option(names = "--max-files", description = "Files rendered in the reviewed diff (default: ${DEFAULT-VALUE})")
        int maxFiles = 50;

        @Option(names = "--max-diff-tokens", description = "Token budget of the reviewed diff (default: ${DEFAULT-VALUE})")
        int maxDiffTokens = 60_000;

        @Option(names = "--max-chunks",
                description = "Chunks of left-out files reviewed separately (default: ${DEFAULT-VALUE})")
        int maxChunks = 4;
    }

    static class GitHub {
        @Option(names = "--mcp", description = "GitHub MCP server: LOCAL (Docker) or REMOTE (default: ${DEFAULT-VALUE})")
        GitHubMcp.Transport mcp = GitHubMcp.Transport.LOCAL;
    }

    @Spec
    CommandSpec spec;

    @Inject
    ReviewService reviews;

    @Inject
    GitHubMcp gitHubMcp;

    @Override
    public Integer call() {
        PrRef ref = parse(pr);
        ReviewOptions options = options();
        PrintWriter out = spec.commandLine().getOut();
        Ansi ansi = spec.commandLine().getColorScheme().ansi();
        gitHubMcp.use(github.mcp);
        Consumer<RunEvent> progress = review.quiet ? event -> {} : new ProgressPrinter(spec.commandLine().getErr(), ansi);
        StdinReviewerConsole console = review.mode == ReviewMode.INTERACTIVE
                ? new StdinReviewerConsole(review.approveOnEof)
                : null;

        ReviewOutcome outcome = reviews.review(new ReviewRequest(ref, review.mode, options, review.out), console, progress);

        if (outcome.written()) {
            printSummary(out, ansi, outcome);
        } else {
            out.println("The report was rejected: nothing was written. Trace in " + outcome.outputDir().toAbsolutePath());
        }
        out.flush();
        return 0;
    }

    private PrRef parse(String value) {
        try {
            return PrUrl.parse(value);
        } catch (IllegalArgumentException e) {
            throw new ParameterException(spec.commandLine(), e.getMessage());
        }
    }

    ReviewOptions options() {
        if (!ModelCatalog.available(models.provider)) {
            throw new ParameterException(spec.commandLine(), "--provider " + models.provider + " needs "
                    + models.provider.apiKeyVariable() + "; set it, or choose another provider");
        }
        if (!DecisionModels.available(models.decisionModel)) {
            throw new ParameterException(spec.commandLine(), "--decision-model " + models.decisionModel + " needs "
                    + models.decisionModel.apiKeyVariable() + "; set it, or choose another decision model");
        }
        double threshold = models.routerThreshold != null
                ? models.routerThreshold
                : models.decisionModel.defaultRouterThreshold();
        if (threshold <= 0 || threshold >= 1) {
            throw new ParameterException(spec.commandLine(), "--router-threshold must be between 0 and 1, exclusive");
        }
        return new ReviewOptions(review.deep, budgets.maxFiles, budgets.maxDiffTokens, models.modelTier,
                budgets.maxDebates, threshold, budgets.maxCost, budgets.maxChunks, models.provider, models.decisionModel);
    }

    static void printSummary(PrintWriter out, Ansi ansi, ReviewOutcome outcome) {
        ReviewReport report = outcome.report();
        String color = switch (report.verdict()) {
            case APPROVE -> "green";
            case COMMENT -> "yellow";
            case REQUEST_CHANGES -> "red";
        };
        out.println();
        out.println(ansi.string(String.format(Locale.ROOT, "%s: @|bold,%s %s|@, overall %.1f/10, confidence %.1f",
                report.pullRequest(), color, report.verdict(), report.scores().overall(), report.scores().confidence())));
        for (MetricScore m : report.scores().metrics()) {
            out.printf(Locale.ROOT, "  %-16s %s%n", m.metric().label(),
                    m.applicable() ? String.format(Locale.ROOT, "%.1f", m.score()) : "n/a");
        }
        out.println("Findings listed as wrong: " + report.wrong().size());
        out.println();
        out.print(outcome.ledger().summary());
        out.println("Report and trace in " + outcome.outputDir().toAbsolutePath());
    }
}
