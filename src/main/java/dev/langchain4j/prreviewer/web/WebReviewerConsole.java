package dev.langchain4j.prreviewer.web;

import dev.langchain4j.prreviewer.hitl.ReviewerConsole;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.PullRequest;
import dev.langchain4j.prreviewer.model.ReviewReport;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * The human reviewer in the browser: the review thread publishes what it needs and blocks, without a timeout, until
 * the reviewer answers through the run page.
 */
final class WebReviewerConsole implements ReviewerConsole {

    private final WebRun run;

    WebReviewerConsole(WebRun run) {
        this.run = run;
    }

    @Override
    public String askClarifications(PullRequest pr, PrSummary summary, List<String> questions) {
        CompletableFuture<String> answer = new CompletableFuture<>();
        run.waitFor(new Interaction.Clarification(List.copyOf(questions), answer));
        return await(answer);
    }

    @Override
    public ApprovalDecision askApproval(ReviewReport report, List<Finding> findings, double spentUsd, double capUsd,
                                        boolean debatesAffordable) {
        CompletableFuture<ApprovalDecision> answer = new CompletableFuture<>();
        run.waitFor(new Interaction.Approval(report, List.copyOf(findings), spentUsd, capUsd, debatesAffordable, answer));
        return await(answer);
    }

    private <T> T await(CompletableFuture<T> answer) {
        try {
            T value = answer.get();
            run.answered();
            return value;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the reviewer", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("The reviewer did not answer", e.getCause());
        }
    }
}
