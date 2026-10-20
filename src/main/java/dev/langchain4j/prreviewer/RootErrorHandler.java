package dev.langchain4j.prreviewer;

import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RetriableException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.prreviewer.github.GitHubAccessException;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import dev.langchain4j.prreviewer.model.DebateOutcome;
import dev.langchain4j.prreviewer.model.DebateVerdict;
import dev.langchain4j.prreviewer.model.DiffStats;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.PullRequest;
import dev.langchain4j.prreviewer.model.ReportDraft;
import dev.langchain4j.prreviewer.model.Severity;
import dev.langchain4j.prreviewer.model.UsefulnessAssessment;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recovery policy of the reviewer: transient failures are retried once (the model clients already retry twice);
 * then a failing step is replaced by a neutral fallback when the review can go on without it, and the failure is
 * disclosed in the report. Only the steps without which there is no review fail the run.
 */
public final class RootErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RootErrorHandler.class);
    private static final int MAX_RETRIES = 1;

    private static final Set<String> REVIEWERS = Set.of("correctnessReviewer", "securityReviewer", "designReviewer",
            "testReviewer", "docsApiReviewer", "correctnessReviewerFallback", "securityReviewerFallback",
            "docsApiReviewerFallback", "chunkReviewer");
    private static final Set<String> VOTERS = Set.of("neutralScorer", "skepticalScorer", "lenientScorer", "scoreVoting");

    private RootErrorHandler() {}

    public static ErrorRecoveryResult recover(ErrorContext context) {
        String agent = context.agentName();
        // a parallel mapper runs copies of its agent named "<agent>_<index>": policies apply to the agent
        String policyName = agent.replaceFirst("_\\d+$", "");
        AgenticScope scope = context.agenticScope();
        Throwable error = context.exception();

        if (hasCause(error, MissingArgumentException.class)) {
            LOG.error("Agent '{}' is missing an argument: this is a wiring bug", agent, error);
            return ErrorRecoveryResult.throwException();
        }
        if (accountProblem(error)) {
            LOG.error("Agent '{}' was refused by the model provider; no agent can succeed: {}", agent, rootMessage(error));
            return ErrorRecoveryResult.throwException();
        }
        if (transientFailure(error) && attempt(scope, agent) <= MAX_RETRIES) {
            LOG.warn("Transient failure of '{}', retrying: {}", agent, rootMessage(error));
            return ErrorRecoveryResult.retry();
        }

        Object fallback = fallback(policyName, scope);
        if (fallback == null) {
            LOG.error("Agent '{}' failed: {}", agent, rootMessage(error));
            return ErrorRecoveryResult.throwException();
        }
        LOG.warn("Agent '{}' failed and is replaced by a fallback: {}", agent, rootMessage(error));
        ScopeState.append(scope, Keys.DEGRADED_AGENTS, agent);
        return ErrorRecoveryResult.result(fallback);
    }

    /** The neutral result that lets the review go on, or null when the failure must stop the run. */
    static Object fallback(String agent, AgenticScope scope) {
        if (REVIEWERS.contains(agent)) {
            return List.of();
        }
        if (VOTERS.contains(agent)) {
            return ScopeState.read(scope, Keys.SCORE_CARD);
        }
        return switch (agent) {
            case "prTriage" -> triageFallback(scope);
            case "contextGatherer" -> "";
            case "questionGenerator" -> List.of();
            case "humanClarification" -> "";
            case "usefulnessAssessor" -> UsefulnessAssessment.unknown();
            case "reviewPanel" -> Map.of();
            case "chunkedReview", "largeDiffGate" -> List.of();
            case "findingProsecutor", "findingDefender" -> "No argument could be produced. UNSURE";
            case "debateJudge", "findingDebate" -> unsure(scope);
            case "reportWriter" -> writerFallback(scope);
            case "reportCritic" -> new dev.langchain4j.prreviewer.model.Critique(1.0, List.of());
            case "reportFinalizer" -> ScopeState.read(scope, Keys.REPORT_DRAFT);
            default -> null;
        };
    }

    /** Without a triage the review goes on with neutral assumptions derived from the diff statistics. */
    private static PrSummary triageFallback(AgenticScope scope) {
        PullRequest pr = ScopeState.read(scope, Keys.PULL_REQUEST);
        DiffStats stats = ScopeState.read(scope, Keys.DIFF_STATS);
        int changed = stats == null ? 0 : stats.additions() + stats.deletions();
        var size = changed < 100 ? dev.langchain4j.prreviewer.model.Size.SMALL
                : changed < 500 ? dev.langchain4j.prreviewer.model.Size.MEDIUM
                : dev.langchain4j.prreviewer.model.Size.LARGE;
        return new PrSummary(pr == null ? "unknown" : pr.title(), dev.langchain4j.prreviewer.model.Category.OTHER,
                size, dev.langchain4j.prreviewer.model.Risk.MEDIUM, List.of(), false, List.of());
    }

    private static DebateVerdict unsure(AgenticScope scope) {
        Finding finding = ScopeState.read(scope, Keys.CONTESTED_FINDING);
        Severity severity = finding == null ? null : finding.severity();
        return new DebateVerdict(DebateOutcome.UNSURE, severity, "The debate could not be completed.");
    }

    private static ReportDraft writerFallback(AgenticScope scope) {
        List<Finding> findings = ScopeState.read(scope, Keys.ACTIVE_FINDINGS);
        return new ReportDraft("The report writer failed; the findings, scores and verdict below are complete, "
                + "but this summary is not.", List.of(), findings.stream().map(Finding::id).toList(), List.of(),
                List.of("The prose of this report could not be written."));
    }

    private static int attempt(AgenticScope scope, String agent) {
        synchronized (RootErrorHandler.class) {
            String key = "attempts." + agent;
            int attempt = scope.readState(key, 0) + 1;
            scope.writeState(key, attempt);
            return attempt;
        }
    }

    /**
     * Failures of the account rather than of the request: invalid key, no credits, model not available. Every later
     * model call would fail the same way, so degrading agent after agent would only produce an empty review.
     */
    static boolean accountProblem(Throwable error) {
        if (hasCause(error, AuthenticationException.class) || hasCause(error, ModelNotFoundException.class)) {
            return true;
        }
        String message = rootMessage(error).toLowerCase(Locale.ROOT);
        return hasCause(error, InvalidRequestException.class)
                && (message.contains("credit balance") || message.contains("billing") || message.contains("quota"));
    }

    /**
     * Worth one more attempt: rate limits, overloads and connection failures. Not timeouts: the model client already
     * retried them twice, and each attempt can run for the whole timeout.
     */
    static boolean transientFailure(Throwable error) {
        if (hasCause(error, TimeoutException.class) || hasCause(error, HttpTimeoutException.class)) {
            return false;
        }
        return hasCause(error, RetriableException.class) || hasCause(error, ConnectException.class)
                || gitHubRateLimited(error);
    }

    /** GitHub refusing a request for its rate limits: the client fails the call instead of waiting for the reset. */
    private static boolean gitHubRateLimited(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof GitHubAccessException github && github.rateLimited()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
