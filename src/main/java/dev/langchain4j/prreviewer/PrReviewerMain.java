package dev.langchain4j.prreviewer;

import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.prreviewer.github.GitHubMcp;
import dev.langchain4j.prreviewer.github.PrUrl;
import dev.langchain4j.prreviewer.hitl.StdinReviewerConsole;
import dev.langchain4j.prreviewer.model.MetricScore;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.PrRef;
import dev.langchain4j.prreviewer.model.ReviewMode;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.models.ChatModelProvider;
import dev.langchain4j.prreviewer.models.ChatModels;
import dev.langchain4j.prreviewer.models.DecisionModels;
import dev.langchain4j.prreviewer.observability.CostLedger;
import dev.langchain4j.prreviewer.observability.CostTrackingListener;
import dev.langchain4j.prreviewer.observability.DecisionCostListener;
import dev.langchain4j.prreviewer.workflow.ReportExporter;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "pr-reviewer", mixinStandardHelpOptions = true, version = "1.0.0",
        description = "Reviews a GitHub pull request with agents built on langchain4j-agentic and Anthropic models. "
                + "Writes report.md, report.json and trace.html; never writes to GitHub.")
public class PrReviewerMain implements Callable<Integer> {

    @Option(names = "--pr", required = true, description = "Pull request URL or owner/repo#number")
    String pr;

    @Option(names = "--mode", defaultValue = "AUTO", description = "AUTO or INTERACTIVE (default: ${DEFAULT-VALUE})")
    ReviewMode mode;

    @Option(names = "--deep", description = "Escalate correctness and security reviews to the premium model")
    boolean deep;

    @Option(names = "--out", defaultValue = "review-output", description = "Output directory (default: ${DEFAULT-VALUE})")
    Path out;

    @Option(names = "--max-files", defaultValue = "50", description = "Files rendered in the reviewed diff")
    int maxFiles;

    @Option(names = "--max-diff-tokens", defaultValue = "60000", description = "Token budget of the reviewed diff")
    int maxDiffTokens;

    @Option(names = "--max-chunks", defaultValue = "4", description = "Chunks of left-out files reviewed separately")
    int maxChunks;

    @Option(names = "--model-tier", description = "Force one tier for every agent: ECONOMY, STANDARD or PREMIUM")
    ModelTier modelTier;

    @Option(names = "--router-threshold",
            description = "Activation threshold of the reviewer router (default: 0.2 for LOCAL, 0.5 otherwise)")
    Double routerThreshold;

    @Option(names = "--max-debates", defaultValue = "3", description = "Contested findings debated per revision")
    int maxDebates;

    @Option(names = "--max-cost", defaultValue = "2.0",
            description = "Soft cost cap in USD: past it voting, debates and escalation are skipped (0 disables it)")
    double maxCost;

    @Option(names = "--mcp", defaultValue = "LOCAL",
            description = "GitHub MCP server: LOCAL (Docker) or REMOTE (api.githubcopilot.com)")
    GitHubMcp.Transport mcp;

    @Option(names = "--decision-model", defaultValue = "LOCAL",
            description = "Reviewer router's decision model: LOCAL (Ollama, tev1:0.8b), TYPESAFE, OPENROUTER or OPENAI")
    DecisionModels.Provider decisionModel;

    @Option(names = "--provider", defaultValue = "ANTHROPIC",
            description = "Chat model provider: ANTHROPIC (Haiku, Sonnet, Opus) or OPENAI (gpt-5-nano, gpt-5.6-luna, gpt-5.1)")
    ChatModelProvider provider;

    @Option(names = "--approve-on-eof", description = "In interactive mode, approve when the input ends")
    boolean approveOnEof;

    public static void main(String[] args) {
        System.exit(new CommandLine(new PrReviewerMain())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setExecutionExceptionHandler((error, cmd, parseResult) -> {
                    cmd.getErr().println("Error: " + rootMessage(error));
                    return 1;
                })
                .execute(args));
    }

    static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return root == error ? message : error.getMessage() + " (caused by " + message + ")";
    }

    @Override
    public Integer call() throws Exception {
        PrRef ref = PrUrl.parse(pr);
        double threshold = routerThreshold != null ? routerThreshold : DecisionModels.defaultRouterThreshold(decisionModel);
        if (threshold <= 0 || threshold >= 1) {
            throw new CommandLine.ParameterException(new CommandLine(this), "--router-threshold must be in (0, 1)");
        }
        ReviewOptions options = new ReviewOptions(deep, maxFiles, maxDiffTokens, modelTier, maxDebates,
                threshold, maxCost, maxChunks);

        CostLedger ledger = new CostLedger();
        ChatModels models = provider.fromEnvironment(List.of(new CostTrackingListener(ledger)));
        DecisionModel decision = DecisionModels.create(decisionModel, new DecisionCostListener(ledger));

        try (McpClient mcpClient = GitHubMcp.client(mcp, GitHubMcp.resolveToken())) {
            AppContext.install(AppContext.builder()
                    .github(GitHubMcp.gateway(mcpClient))
                    .githubTools(GitHubMcp.readTools(mcpClient))
                    .models(models)
                    .decisionModel(decision)
                    .console(mode == ReviewMode.INTERACTIVE ? new StdinReviewerConsole(approveOnEof) : null)
                    .ledger(ledger)
                    .options(options)
                    .outputDir(out)
                    .build());

            PrReviewSystem system = PrReviewSystems.create();
            Path dir = ReportExporter.outputDirectory(ref);
            try {
                ReviewReport report = system.review(ref.toString(), mode, options);
                if (Files.exists(dir.resolve("report.md"))) {
                    printSummary(System.out, report, dir, ledger);
                } else {
                    System.out.println("The report was rejected: nothing was written. Trace in " + dir.toAbsolutePath());
                }
                return 0;
            } finally {
                Files.createDirectories(dir);
                HtmlReportGenerator.generateReport(system.agentMonitor(), dir.resolve("trace.html"));
            }
        }
    }

    static void printSummary(PrintStream out, ReviewReport report, Path dir, CostLedger ledger) {
        out.println();
        out.printf(Locale.ROOT, "%s: %s, overall %.1f/10, confidence %.1f%n", report.pullRequest(), report.verdict(),
                report.scores().overall(), report.scores().confidence());
        for (MetricScore m : report.scores().metrics()) {
            out.printf(Locale.ROOT, "  %-16s %s%n", m.metric().label(),
                    m.applicable() ? String.format(Locale.ROOT, "%.1f", m.score()) : "n/a");
        }
        out.println("Findings listed as wrong: " + report.wrong().size());
        out.println();
        out.print(ledger.summary());
        out.println("Report and trace in " + dir.toAbsolutePath());
    }
}
