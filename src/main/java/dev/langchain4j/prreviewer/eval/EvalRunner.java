package dev.langchain4j.prreviewer.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.prreviewer.AppContext;
import dev.langchain4j.prreviewer.PrReviewSystem;
import dev.langchain4j.prreviewer.PrReviewSystems;
import dev.langchain4j.prreviewer.github.GitHubMcp;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.MetricScore;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.ReviewMode;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.model.Verdict;
import dev.langchain4j.prreviewer.models.ChatModelProvider;
import dev.langchain4j.prreviewer.models.DecisionModels;
import dev.langchain4j.prreviewer.observability.CostLedger;
import dev.langchain4j.prreviewer.observability.CostTrackingListener;
import dev.langchain4j.prreviewer.observability.DecisionCostListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Runs the reviewer in automatic mode on every case of the eval set, several times, and reports verdict agreement,
 * must-find recall, unmatched findings, score stability, cost and latency. Every run calls the models: check the
 * cost of one case before running the whole set.
 */
@Command(name = "run-eval", mixinStandardHelpOptions = true, description = "Scores the reviewer against the eval set")
public class EvalRunner implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Option(names = "--eval", defaultValue = "eval/eval-set.json")
    Path eval;

    @Option(names = "--runs", defaultValue = "3", description = "Runs per case, for score stability")
    int runs;

    @Option(names = "--limit", defaultValue = "1000", description = "Number of cases to run")
    int limit;

    @Option(names = "--model-tier", description = "Force a tier for every agent")
    ModelTier modelTier;

    @Option(names = "--router-threshold", description = "Default: 0.2 for LOCAL, 0.5 otherwise")
    Double routerThreshold;

    @Option(names = "--max-cost", defaultValue = "2.0", description = "Soft cost cap per review")
    double maxCost;

    @Option(names = "--decision-model", defaultValue = "LOCAL")
    DecisionModels.Provider decisionModel;

    @Option(names = "--provider", defaultValue = "ANTHROPIC", description = "ANTHROPIC or OPENAI")
    ChatModelProvider provider;

    @Option(names = "--out", defaultValue = "eval/results")
    Path out;

    public static void main(String[] args) {
        System.exit(new CommandLine(new EvalRunner()).setCaseInsensitiveEnumValuesAllowed(true).execute(args));
    }

    @Override
    public Integer call() throws Exception {
        List<EvalCase> cases = JSON.readValue(eval.toFile(), new TypeReference<List<EvalCase>>() {});
        double threshold = routerThreshold != null ? routerThreshold : DecisionModels.defaultRouterThreshold(decisionModel);
        ReviewOptions options = new ReviewOptions(false, 50, 60_000, modelTier, 3, threshold, maxCost, 4);
        Map<String, Object> results = new LinkedHashMap<>();
        List<Map<String, Object>> perCase = new ArrayList<>();
        List<Double> agreement = new ArrayList<>();
        List<Double> recalls = new ArrayList<>();
        List<Double> unmatched = new ArrayList<>();
        List<Double> stability = new ArrayList<>();
        List<Double> costs = new ArrayList<>();
        List<Double> seconds = new ArrayList<>();

        Files.createDirectories(out);
        Path file = out.resolve("results-" + Instant.now().toString().replace(':', '-') + ".json");
        try (McpClient client = GitHubMcp.client(GitHubMcp.Transport.LOCAL, GitHubMcp.resolveToken())) {
            for (EvalCase c : cases.subList(0, Math.min(limit, cases.size()))) {
                List<EvalMetrics.Run> caseRuns = new ArrayList<>();
                List<String> failures = new ArrayList<>();
                for (int i = 0; i < runs; i++) {
                    try {
                        caseRuns.add(runOnce(client, c, options));
                    } catch (RuntimeException e) {
                        failures.add(String.valueOf(e.getMessage()));
                        System.err.printf(Locale.ROOT, "%s run %d failed: %s%n", c.pr(), i + 1, e.getMessage());
                    }
                }
                if (caseRuns.isEmpty()) {
                    Map<String, Object> failed = new LinkedHashMap<>();
                    failed.put("pr", c.pr());
                    failed.put("expected", c.expectedVerdict());
                    failed.put("failures", failures);
                    perCase.add(failed);
                    results.put("cases", perCase);
                    JSON.writeValue(file.toFile(), results);
                    continue;
                }
                Verdict majority = EvalMetrics.majorityVerdict(caseRuns);
                agreement.add(majority == c.expectedVerdict() ? 1.0 : 0.0);
                caseRuns.forEach(r -> {
                    recalls.add(EvalMetrics.recall(c, r));
                    unmatched.add(EvalMetrics.unmatchedRate(c, r));
                    costs.add(r.costUsd());
                    seconds.add(r.seconds());
                });
                double sd = EvalMetrics.standardDeviation(caseRuns.stream().map(EvalMetrics.Run::overall).toList());
                stability.add(sd);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("pr", c.pr());
                entry.put("expected", c.expectedVerdict());
                entry.put("majority", majority);
                entry.put("runs", caseRuns.stream().map(r -> Map.of("verdict", r.verdict(), "overall", r.overall(),
                        "scores", r.scores(), "findings", r.findings().size(), "costUsd", r.costUsd(),
                        "seconds", r.seconds(), "recall", String.valueOf(EvalMetrics.recall(c, r)))).toList());
                entry.put("overallStdDev", sd);
                if (!failures.isEmpty()) {
                    entry.put("failures", failures);
                }
                perCase.add(entry);
                results.put("cases", perCase);
                JSON.writeValue(file.toFile(), results);   // partial results survive a later failure
                System.out.printf(Locale.ROOT, "%-40s expected %-16s got %-16s sd %.2f%n", c.pr(), c.expectedVerdict(),
                        majority, sd);
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("cases", perCase.size());
        summary.put("runsPerCase", runs);
        summary.put("verdictAgreement", EvalMetrics.mean(agreement));
        summary.put("mustFindRecall", EvalMetrics.mean(recalls));
        summary.put("unmatchedFindingRate", EvalMetrics.mean(unmatched));
        summary.put("overallScoreStdDev", EvalMetrics.mean(stability));
        summary.put("meanCostUsd", EvalMetrics.mean(costs));
        summary.put("meanSeconds", EvalMetrics.mean(seconds));
        summary.put("provider", provider);
        summary.put("decisionModel", decisionModel);
        summary.put("routerThreshold", threshold);
        results.put("summary", summary);
        results.put("cases", perCase);
        JSON.writeValue(file.toFile(), results);
        System.out.println(JSON.writeValueAsString(summary));
        System.out.println("Results written to " + file);
        return 0;
    }

    private EvalMetrics.Run runOnce(McpClient client, EvalCase c, ReviewOptions options) {
        CostLedger ledger = new CostLedger();
        AppContext.install(AppContext.builder()
                .github(GitHubMcp.gateway(client))
                .githubTools(GitHubMcp.readTools(client))
                .models(provider.fromEnvironment(List.of(new CostTrackingListener(ledger))))
                .decisionModel(DecisionModels.create(decisionModel, new DecisionCostListener(ledger)))
                .ledger(ledger)
                .options(options)
                .outputDir(out.resolve("reports"))
                .build());
        PrReviewSystem system = PrReviewSystems.create();
        long start = System.nanoTime();
        ReviewReport report = system.review(c.pr(), ReviewMode.AUTO, options);
        double elapsed = (System.nanoTime() - start) / 1e9;
        List<Finding> findings = system.agentMonitor().successfulExecutions().get(0).agenticScope()
                .readState(Keys.ActiveFindings.class);
        Map<String, Double> scores = new LinkedHashMap<>();
        for (MetricScore m : report.scores().metrics()) {
            if (m.applicable()) {
                scores.put(m.metric().name(), m.score());
            }
        }
        return new EvalMetrics.Run(report.verdict(), report.scores().overall(), scores, findings, ledger.total(), elapsed);
    }
}
