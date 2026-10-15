package dev.langchain4j.prreviewer.app;

import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.prreviewer.PrReviewSystem;
import dev.langchain4j.prreviewer.config.ReviewerConfig;
import dev.langchain4j.prreviewer.hitl.ReviewerConsole;
import dev.langchain4j.prreviewer.model.PrRef;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.observability.CostLedger;
import io.quarkus.arc.ClientProxy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Runs reviews for the CLI and the web interface, one at a time. */
@ApplicationScoped
public class ReviewService {

    @Inject
    PrReviewSystem system;

    @Inject
    ActiveReview activeReview;

    @Inject
    ReviewerConfig config;

    /**
     * Reviews a pull request on the calling thread. The request context is activated for the agents invoked outside
     * HTTP; the monitor is cleared first, so the trace covers this review only.
     *
     * @param console the human channel, or null in automatic mode
     */
    @ActivateRequestContext
    public ReviewOutcome review(ReviewRequest request, ReviewerConsole console, Consumer<RunEvent> events) {
        Path dir = outputDirectory(request.outputRoot() != null ? request.outputRoot() : config.outputDir(), request.pr());
        ReviewRun run = new ReviewRun(request, new CostLedger(), console, dir, events);
        long start = System.currentTimeMillis();
        activeReview.start(run);
        AgentMonitor monitor = system.agentMonitor();
        monitor.clear();
        try {
            ReviewReport report = system.review(request.pr().toString(), request.mode(), request.options());
            return new ReviewOutcome(report, dir, writtenSince(dir.resolve("report.md"), start), run.ledger());
        } finally {
            writeTrace(monitor, dir);
            activeReview.end();
        }
    }

    /** The directory of a pull request's review under the configured output directory. */
    public Path outputDirectory(PrRef ref) {
        return outputDirectory(config.outputDir(), ref);
    }

    private static Path outputDirectory(Path root, PrRef ref) {
        return root.resolve(ref.owner() + "-" + ref.repo() + "-" + ref.number());
    }

    /** The root agent itself, not its client proxy, for the topology report. */
    public Object rootAgent() {
        return ClientProxy.unwrap(system);
    }

    private static boolean writtenSince(Path file, long start) {
        try {
            return Files.exists(file) && Files.getLastModifiedTime(file).toMillis() >= start;
        } catch (IOException e) {
            return false;
        }
    }

    private static void writeTrace(AgentMonitor monitor, Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create " + dir, e);
        }
        HtmlReportGenerator.generateReport(monitor, dir.resolve("trace.html"));
    }
}
