package dev.langchain4j.prreviewer.web;

import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ReviewReport;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** What a review waits for from the human reviewer; the review thread blocks on {@link #answer()}. */
public sealed interface Interaction {

    CompletableFuture<?> answer();

    record Clarification(List<String> questions, CompletableFuture<String> answer) implements Interaction {}

    record Approval(ReviewReport report, List<Finding> findings, double spentUsd, double capUsd,
                    boolean debatesAffordable, CompletableFuture<ApprovalDecision> answer) implements Interaction {}
}
