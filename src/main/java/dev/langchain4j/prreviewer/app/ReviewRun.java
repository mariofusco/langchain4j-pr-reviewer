package dev.langchain4j.prreviewer.app;

import dev.langchain4j.prreviewer.hitl.ReviewerConsole;
import dev.langchain4j.prreviewer.observability.CostLedger;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * One review in progress: its request, cost ledger, human channel, output directory and event sink.
 *
 * @param console the human channel, or null in automatic mode
 */
public record ReviewRun(ReviewRequest request, CostLedger ledger, ReviewerConsole console, Path outputDir,
                        Consumer<RunEvent> events) {}
