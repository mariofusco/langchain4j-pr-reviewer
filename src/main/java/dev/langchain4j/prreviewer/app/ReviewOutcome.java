package dev.langchain4j.prreviewer.app;

import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.observability.CostLedger;
import java.nio.file.Path;

/**
 * The result of a review.
 *
 * @param written false when the reviewer rejected the report: only the trace was written
 */
public record ReviewOutcome(ReviewReport report, Path outputDir, boolean written, CostLedger ledger) {}
