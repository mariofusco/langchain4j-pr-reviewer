package dev.langchain4j.prreviewer;

import static dev.langchain4j.prreviewer.support.TestData.MAIN_FILE;
import static dev.langchain4j.prreviewer.support.TestData.VALIDATION_FILE;
import static dev.langchain4j.prreviewer.support.TestData.card;
import static dev.langchain4j.prreviewer.support.TestData.judge;
import static dev.langchain4j.prreviewer.support.TestData.raw;
import static dev.langchain4j.prreviewer.support.TestData.summary;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.prreviewer.app.ReviewOutcome;
import dev.langchain4j.prreviewer.app.ReviewRequest;
import dev.langchain4j.prreviewer.app.ReviewService;
import dev.langchain4j.prreviewer.github.DiffParser;
import dev.langchain4j.prreviewer.github.PrUrl;
import dev.langchain4j.prreviewer.hitl.ReviewerConsole;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import dev.langchain4j.prreviewer.model.ApprovalDecision.Decision;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.Category;
import dev.langchain4j.prreviewer.model.DecisionProvider;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.FindingCategory;
import dev.langchain4j.prreviewer.model.ModelProvider;
import dev.langchain4j.prreviewer.model.PrRef;
import dev.langchain4j.prreviewer.model.RawFinding;
import dev.langchain4j.prreviewer.model.ReviewMode;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.model.Risk;
import dev.langchain4j.prreviewer.model.Severity;
import dev.langchain4j.prreviewer.model.Verdict;
import dev.langchain4j.prreviewer.model.Verification;
import dev.langchain4j.prreviewer.support.FakeDecisionModel;
import dev.langchain4j.prreviewer.support.FixtureGitHubReads;
import dev.langchain4j.prreviewer.support.ScriptedChatModel;
import dev.langchain4j.prreviewer.support.ScriptedReviewerConsole;
import dev.langchain4j.prreviewer.support.TestData;
import dev.langchain4j.prreviewer.support.TestScenario;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Orchestration tests: the real agentic system built by Quarkus, with every model answered by a scripted model, the
 * decision model by a fake and GitHub by fixtures.
 */
@QuarkusTest
class PrReviewSystemTest {

    static final String PR = "https://github.com/langchain4j/langchain4j/pull/6610";
    static final Map<String, Double> CORRECTNESS_AND_DESIGN = Map.of("correctnessReviewer", 0.9, "designReviewer", 0.7);

    static final String SQL_FIX = """
            diff --git a/core/src/main/java/com/acme/UserRepository.java b/core/src/main/java/com/acme/UserRepository.java
            --- a/core/src/main/java/com/acme/UserRepository.java
            +++ b/core/src/main/java/com/acme/UserRepository.java
            @@ -10,3 +10,4 @@ public class UserRepository {
                 public User findByName(String name) throws SQLException {
            -        return null;
            +        Statement statement = connection.createStatement();
            +        return map(statement.executeQuery("select * from users where name = '" + name + "'"));
                 }
            """;

    static final String README = """
            diff --git a/README.md b/README.md
            --- a/README.md
            +++ b/README.md
            @@ -1,2 +1,2 @@
             # Acme
            -Install with Maven.
            +Install with Maven or Gradle.
            """;

    @Inject
    ReviewService reviews;

    @Inject
    PrReviewSystem system;

    @Inject
    TestScenario scenario;

    Map<String, Object> responses;
    String diffOverride;
    ReviewOutcome outcome;

    @BeforeEach
    void defaults() throws IOException {
        responses = TestData.defaultResponses();
        diffOverride = null;
        deleteRecursively(reviews.outputDirectory(ref()));
    }

    private ReviewReport review(FakeDecisionModel router, ReviewerConsole console, ReviewMode mode, ReviewOptions options) {
        scenario.reset()
                .responses(responses)
                .decisionModel(router)
                .github(new FixtureGitHubReads("pr6610", diffOverride));
        outcome = reviews.review(new ReviewRequest(ref(), mode, options), console, event -> {});
        return outcome.report();
    }

    private ReviewReport review(Map<String, Double> routing) {
        return review(new FakeDecisionModel(routing), null, ReviewMode.AUTO, ReviewOptions.defaults());
    }

    private static ReviewOptions options(int maxFiles, int maxDiffTokens, double maxCost, int maxChunks) {
        return new ReviewOptions(false, maxFiles, maxDiffTokens, null, 3, 0.5, maxCost, maxChunks,
                ModelProvider.ANTHROPIC, DecisionProvider.LOCAL);
    }

    /** Model calls made by the agent, including the copies of a mapped agent ({@code chunkReviewer_0}, ...). */
    private int invocations(String agent) {
        return (int) scenario.calls().stream()
                .filter(c -> c.agent().equals(agent) || c.agent().startsWith(agent + "_"))
                .count();
    }

    private AgenticScope scope() {
        return system.agentMonitor().successfulExecutions().get(0).agenticScope();
    }

    private static PrRef ref() {
        return PrUrl.parse(PR);
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    @Test
    void automatic_review_runs_the_routed_reviewers_and_writes_the_report() throws Exception {
        FakeDecisionModel router = new FakeDecisionModel(CORRECTNESS_AND_DESIGN);

        ReviewReport report = review(router, null, ReviewMode.AUTO, ReviewOptions.defaults());

        assertThat(invocations("correctnessReviewer")).isEqualTo(1);
        assertThat(invocations("designReviewer")).isEqualTo(1);
        assertThat(invocations("securityReviewer")).isZero();
        assertThat(invocations("contextGatherer")).isZero();
        assertThat(invocations("questionGenerator")).isZero();
        assertThat(invocations("neutralScorer")).isZero();
        assertThat(invocations("findingProsecutor")).isZero();
        assertThat(invocations("reportWriter")).isEqualTo(1);

        // the scorer proposed APPROVE, but a MEDIUM finding sets the floor to COMMENT
        assertThat(report.verdict()).isEqualTo(Verdict.COMMENT);
        assertThat(report.verdictReasoning()).contains("Rule applied");
        assertThat(report.wrong()).extracting(Finding::id).containsExactly("F1");
        assertThat(report.improvements()).anyMatch(i -> i.startsWith("Minor: **F2"));
        assertThat(report.run().reviewersRun()).containsExactlyInAnyOrder("correctnessReviewer", "designReviewer");

        // only the compact routing input reaches the decision model
        assertThat(router.requests()).hasSize(1);
        assertThat(router.requests().get(0).input().toString()).contains("routingInput").doesNotContain("diff --git");

        Path dir = outcome.outputDir();
        assertThat(Files.readString(dir.resolve("report.md"))).contains("**Verdict:** COMMENT").contains("### Scores");
        assertThat(dir.resolve("report.json")).exists();
        assertThat(dir.resolve("trace.html")).exists();
        assertThat(outcome.written()).isTrue();
        assertThat(system.agentMonitor().successfulExecutions()).hasSize(1);
    }

    @Test
    void interactive_review_asks_questions_debates_and_revises_after_a_contest() {
        AtomicInteger judgements = new AtomicInteger();
        responses.put("prTriage", summary(Category.BUGFIX, Risk.HIGH, true));
        responses.put("correctnessReviewer", List.of(raw(FindingCategory.CORRECTNESS, Severity.HIGH, MAIN_FILE, 130, 0.6)));
        // the first debate confirms the finding, the one requested by the reviewer rejects it
        responses.put("debateJudge", (Function<ScriptedChatModel.Call, Object>) call -> judgements.incrementAndGet() == 1
                ? judge("CONFIRMED", "Real.")
                : judge("REJECTED", "The guard makes it impossible."));
        ScriptedReviewerConsole console = new ScriptedReviewerConsole("Backward compatibility is not required.",
                new ApprovalDecision(Decision.CONTEST, "there is a guard in AiServices", List.of("F1"), List.of("F2")),
                ApprovalDecision.approved());

        ReviewReport report = review(new FakeDecisionModel(CORRECTNESS_AND_DESIGN), console, ReviewMode.INTERACTIVE,
                ReviewOptions.defaults());

        assertThat(invocations("contextGatherer")).isEqualTo(1);
        assertThat(console.questionsAsked).containsExactly(List.of("Should default methods ever call the model?"));
        assertThat(invocations("debateJudge")).isEqualTo(2);
        assertThat(invocations("findingProsecutor")).isEqualTo(4); // two rounds per debate, no convergence
        assertThat(invocations("scorer")).isEqualTo(2);
        assertThat(console.reportsShown).hasSize(2);
        // before the contest: the confirmed HIGH correctness finding forces REQUEST_CHANGES
        assertThat(console.reportsShown.get(0).verdict()).isEqualTo(Verdict.REQUEST_CHANGES);
        assertThat(console.reportsShown.get(0).wrong().get(0).verification()).isEqualTo(Verification.CONFIRMED);

        assertThat(report.verdict()).isEqualTo(Verdict.APPROVE);
        assertThat(report.wrong()).isEmpty();
        assertThat(report.run().revisionRounds()).isEqualTo(1);
        List<Finding> findings = ScopeState.read(scope(), Keys.FINDINGS);
        assertThat(findings).extracting(Finding::verification)
                .containsExactly(Verification.REJECTED, Verification.DROPPED_BY_REVIEWER);
        assertThat(ScopeState.<String>read(scope(), Keys.REVIEWER_GUIDANCE))
                .contains("Backward compatibility is not required.")
                .contains("contested F1").contains("dropped F2").contains("there is a guard in AiServices");
    }

    @Test
    void borderline_score_card_is_settled_by_three_voters() {
        responses.put("correctnessReviewer", List.of());
        responses.put("scorer", card(7.0, Verdict.COMMENT));

        ReviewReport report = review(CORRECTNESS_AND_DESIGN);

        assertThat(invocations("neutralScorer")).isEqualTo(1);
        assertThat(invocations("skepticalScorer")).isEqualTo(1);
        assertThat(invocations("lenientScorer")).isEqualTo(1);
        assertThat(report.scores().overall()).isEqualTo(7.0); // mean of 7.0, 6.0 and 8.0
        assertThat(report.verdict()).isEqualTo(Verdict.COMMENT); // two COMMENT votes against one APPROVE
        assertThat(report.verdictReasoning()).contains("independent scorers");
    }

    @Test
    void clear_score_card_skips_the_vote() {
        responses.put("correctnessReviewer", List.of());

        review(CORRECTNESS_AND_DESIGN);

        assertThat(invocations("neutralScorer")).isZero();
    }

    @Test
    void production_code_skipped_by_the_router_gets_a_correctness_review_from_the_floor() {
        ReviewReport report = review(Map.of("designReviewer", 0.9));

        assertThat(invocations("correctnessReviewer")).isZero();
        assertThat(invocations("correctnessReviewerFallback")).isEqualTo(1);
        assertThat(invocations("securityReviewerFallback")).isZero();
        assertThat(report.run().reviewersRun()).containsExactly("designReviewer", "correctnessReviewerFallback");
        assertThat(report.improvements()).anyMatch(i -> i.startsWith("Minor:") && i.contains("CORRECTNESS"));
    }

    @Test
    void security_sensitive_code_skipped_by_the_router_gets_a_security_review_from_the_floor() {
        diffOverride = SQL_FIX;
        responses.put("correctnessReviewer", List.of());

        ReviewReport report = review(Map.of("correctnessReviewer", 0.9));

        assertThat(invocations("correctnessReviewerFallback")).isZero();
        assertThat(invocations("securityReviewerFallback")).isEqualTo(1);
        assertThat(report.run().reviewersRun()).containsExactly("correctnessReviewer", "securityReviewerFallback");
    }

    @Test
    void documentation_only_change_gets_only_a_documentation_review() {
        diffOverride = README;
        responses.put("prTriage", summary(Category.DOCS, Risk.LOW, false));

        ReviewReport report = review(Map.of());

        assertThat(report.run().reviewersRun()).containsExactly("docsApiReviewerFallback");
        assertThat(invocations("correctnessReviewerFallback")).isZero();
    }

    @Test
    void failing_decision_model_degrades_to_the_fallback_reviewer() {
        ReviewReport report = review(FakeDecisionModel.failing(), null, ReviewMode.AUTO, ReviewOptions.defaults());

        assertThat(invocations("correctnessReviewerFallback")).isEqualTo(1);
        assertThat(report.limitations()).anyMatch(l -> l.contains("reviewPanel"));
    }

    @Test
    void failing_reviewer_is_disclosed_instead_of_failing_the_review() {
        responses.put("correctnessReviewer", new IllegalStateException("correctnessReviewer failed"));

        ReviewReport report = review(CORRECTNESS_AND_DESIGN);

        assertThat(report.limitations()).anyMatch(l -> l.contains("correctnessReviewer"));
        assertThat(report.scores().confidence()).isLessThanOrEqualTo(0.6);
        assertThat(report.improvements()).anyMatch(i -> i.startsWith("Minor: **F1"));
    }

    @Test
    void account_problems_fail_the_review_at_the_first_agent() {
        responses.put("correctnessReviewer", new InvalidRequestException(
                "{\"type\":\"error\",\"error\":{\"message\":\"Your credit balance is too low to access the Anthropic API.\"}}"));

        assertThatThrownBy(() -> review(CORRECTNESS_AND_DESIGN)).hasStackTraceContaining("credit balance");
        assertThat(invocations("scorer")).isZero();
    }

    @Test
    void failing_scorer_fails_the_review() {
        responses.put("scorer", new IllegalStateException("scorer failed"));

        assertThatThrownBy(() -> review(CORRECTNESS_AND_DESIGN)).hasStackTraceContaining("scorer failed");
    }

    @Test
    void cost_cap_skips_debates_and_votes_but_completes_the_review() {
        responses.put("correctnessReviewer", List.of(raw(FindingCategory.CORRECTNESS, Severity.HIGH, MAIN_FILE, 130, 0.6)));
        responses.put("scorer", card(7.0, Verdict.COMMENT));

        // the triage and the reviewers alone spend more than this
        ReviewReport report = review(new FakeDecisionModel(CORRECTNESS_AND_DESIGN), null, ReviewMode.AUTO,
                options(50, 60_000, 0.001, 4));

        assertThat(invocations("debateJudge")).isZero();
        assertThat(invocations("neutralScorer")).isZero();
        assertThat(report.wrong().get(0).verification()).isEqualTo(Verification.UNSURE);
        assertThat(report.limitations()).anyMatch(l -> l.contains("cost cap"));
        assertThat(outcome.ledger().capEvents()).anyMatch(e -> e.startsWith("debate of F1"))
                .anyMatch(e -> e.startsWith("score voting"));
    }

    @Test
    void very_large_diff_is_reviewed_in_chunks() {
        int files = DiffParser.parse(FixtureGitHubReads.diff6610()).size();

        ReviewReport report = review(new FakeDecisionModel(CORRECTNESS_AND_DESIGN), null, ReviewMode.AUTO,
                options(2, 60_000, 2.0, 4));

        List<String> chunks = ScopeState.read(scope(), Keys.DIFF_CHUNKS);
        assertThat(files).isGreaterThan(2);
        assertThat(chunks).isNotEmpty();
        assertThat(invocations("chunkReviewer")).isEqualTo(chunks.size());
        assertThat(report.run().reviewersRun()).contains("chunkReviewer");
        assertThat(report.limitations()).anyMatch(l -> l.startsWith("Not every file was reviewed in full"));
        assertThat(report.scores().confidence()).isLessThanOrEqualTo(0.6);
    }

    @Test
    void failing_chunk_reviewer_keeps_the_findings_of_the_other_chunks() {
        // one file in the main diff, the rest chunked at 4K tokens: several chunks, one of which fails
        responses.put("chunkReviewer", (Function<ScriptedChatModel.Call, Object>) call ->
                call.user().contains("### File: langchain4j/src/test/java/dev/langchain4j/service/ToolErrorHandlingNoticeTest.java")
                        ? new IllegalStateException("malformed chunk answer")
                        : List.of(raw(FindingCategory.CORRECTNESS, Severity.LOW, MAIN_FILE, 125, 0.6)));

        ReviewReport report = review(new FakeDecisionModel(CORRECTNESS_AND_DESIGN), null, ReviewMode.AUTO,
                options(1, 8_000, 2.0, 10));

        List<String> chunks = ScopeState.read(scope(), Keys.DIFF_CHUNKS);
        List<List<RawFinding>> chunkFindings = ScopeState.read(scope(), Keys.CHUNK_FINDINGS);
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunkFindings).hasSize(chunks.size());
        assertThat(chunkFindings.stream().filter(List::isEmpty)).hasSize(1);                 // only the failed chunk
        assertThat(invocations("chunkReviewer")).isEqualTo(chunks.size());
        assertThat(ScopeState.<List<String>>read(scope(), Keys.DEGRADED_AGENTS)).singleElement().asString()
                .startsWith("chunkReviewer_");
        assertThat(report.improvements()).anyMatch(i -> i.startsWith("Minor:") && i.contains("CORRECTNESS"));
    }

    @Test
    void rejected_report_is_not_written() {
        ScriptedReviewerConsole console = new ScriptedReviewerConsole("",
                new ApprovalDecision(Decision.REJECTED, "", List.of(), List.of()));

        review(new FakeDecisionModel(CORRECTNESS_AND_DESIGN), console, ReviewMode.INTERACTIVE, ReviewOptions.defaults());

        assertThat(outcome.written()).isFalse();
        assertThat(outcome.outputDir().resolve("report.md")).doesNotExist();
        assertThat(outcome.outputDir().resolve("trace.html")).exists();
    }

    @Test
    void edit_notes_are_applied_by_the_finalizer() {
        ScriptedReviewerConsole console = new ScriptedReviewerConsole("",
                new ApprovalDecision(Decision.EDIT, "mention the changelog", List.of(), List.of()));

        ReviewReport report = review(new FakeDecisionModel(CORRECTNESS_AND_DESIGN), console, ReviewMode.INTERACTIVE,
                ReviewOptions.defaults());

        assertThat(invocations("reportFinalizer")).isEqualTo(1);
        assertThat(report.summary()).endsWith("(edited by the reviewer)");
    }

    @Test
    void contest_after_the_last_revision_round_is_applied_without_a_debate() {
        ApprovalDecision contest = new ApprovalDecision(Decision.CONTEST, "", List.of("F1"), List.of());
        ScriptedReviewerConsole console = new ScriptedReviewerConsole("", contest, contest,
                new ApprovalDecision(Decision.CONTEST, "F2 is irrelevant", List.of("F1"), List.of("F2")));

        ReviewReport report = review(new FakeDecisionModel(CORRECTNESS_AND_DESIGN), console, ReviewMode.INTERACTIVE,
                ReviewOptions.defaults());

        assertThat(console.reportsShown).hasSize(3);
        assertThat(invocations("debateJudge")).isEqualTo(2); // the third contest comes too late for a debate
        assertThat(invocations("reportFinalizer")).isEqualTo(1);
        assertThat(report.wrong()).extracting(Finding::verification).containsExactly(Verification.UNSURE);
        assertThat(report.improvements()).noneMatch(i -> i.contains("F2"));
        assertThat(report.limitations()).anyMatch(l -> l.contains("could not be debated"));
    }

    @Test
    void security_reviewer_findings_on_lines_outside_the_diff_are_discarded() {
        responses.put("securityReviewer", List.of(
                raw(FindingCategory.SECURITY, Severity.CRITICAL, MAIN_FILE, 900, 0.9),
                raw(FindingCategory.SECURITY, Severity.CRITICAL, "src/main/java/Invented.java", 1, 0.9)));

        ReviewReport report = review(Map.of("correctnessReviewer", 0.9, "securityReviewer", 0.9));

        assertThat(report.wrong()).noneMatch(f -> f.category() == FindingCategory.SECURITY);
        assertThat(report.limitations()).anyMatch(l -> l.startsWith("2 findings were discarded"));
        assertThat(report.verdict()).isNotEqualTo(Verdict.REQUEST_CHANGES);
    }
}
