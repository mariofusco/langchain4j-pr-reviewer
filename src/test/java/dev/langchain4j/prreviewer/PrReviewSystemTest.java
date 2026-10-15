package dev.langchain4j.prreviewer;

import static dev.langchain4j.prreviewer.support.StubAgents.MAIN_FILE;
import static dev.langchain4j.prreviewer.support.StubAgents.raw;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.agents.CorrectnessReviewer;
import dev.langchain4j.prreviewer.agents.DebateJudge;
import dev.langchain4j.prreviewer.agents.PrTriage;
import dev.langchain4j.prreviewer.agents.Scorer;
import dev.langchain4j.prreviewer.agents.SecurityReviewer;
import dev.langchain4j.prreviewer.github.DiffParser;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.ApprovalDecision.Decision;
import dev.langchain4j.prreviewer.model.Category;
import dev.langchain4j.prreviewer.model.DebateOutcome;
import dev.langchain4j.prreviewer.model.DebateVerdict;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.FindingCategory;
import dev.langchain4j.prreviewer.model.ReviewMode;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.model.Risk;
import dev.langchain4j.prreviewer.model.Severity;
import dev.langchain4j.prreviewer.model.Verdict;
import dev.langchain4j.prreviewer.model.Verification;
import dev.langchain4j.prreviewer.observability.CostLedger;
import dev.langchain4j.prreviewer.support.FakeDecisionModel;
import dev.langchain4j.prreviewer.support.FixtureGitHubGateway;
import dev.langchain4j.prreviewer.support.ScriptedReviewerConsole;
import dev.langchain4j.prreviewer.support.StubAgents;
import dev.langchain4j.prreviewer.support.TestContexts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Orchestration tests: the real agentic system, with every AI agent replaced by a stub and GitHub by fixtures. */
class PrReviewSystemTest {

    static final String PR = "https://github.com/langchain4j/langchain4j/pull/6610";
    static final Map<String, Double> CORRECTNESS_AND_DESIGN = Map.of("correctnessReviewer", 0.9, "designReviewer", 0.7);

    @TempDir
    Path out;

    PrReviewSystem system;

    @AfterEach
    void uninstall() {
        AppContext.install(null);
    }

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

    String diffOverride;

    private ReviewReport review(StubAgents stubs, FakeDecisionModel router, ScriptedReviewerConsole console,
                                ReviewMode mode, ReviewOptions options, CostLedger ledger) {
        TestContexts.install(new FixtureGitHubGateway("pr6610", diffOverride), TestContexts.NO_MODELS, router, console,
                options, ledger, out);
        system = PrReviewSystems.create(stubs.resolver());
        return system.review(PR, mode, options);
    }

    private ReviewReport review(StubAgents stubs, Map<String, Double> routing) {
        return review(stubs, new FakeDecisionModel(routing), null, ReviewMode.AUTO, ReviewOptions.defaults(),
                new CostLedger());
    }

    private AgenticScope scope() {
        return system.agentMonitor().successfulExecutions().get(0).agenticScope();
    }

    @Test
    void automatic_review_runs_the_routed_reviewers_and_writes_the_report() throws Exception {
        StubAgents stubs = StubAgents.defaults();
        FakeDecisionModel router = new FakeDecisionModel(CORRECTNESS_AND_DESIGN);

        ReviewReport report = review(stubs, router, null, ReviewMode.AUTO, ReviewOptions.defaults(), new CostLedger());

        assertThat(stubs.invocations("correctnessReviewer")).isEqualTo(1);
        assertThat(stubs.invocations("designReviewer")).isEqualTo(1);
        assertThat(stubs.invocations("securityReviewer")).isZero();
        assertThat(stubs.invocations("contextGatherer")).isZero();
        assertThat(stubs.invocations("questionGenerator")).isZero();
        assertThat(stubs.invocations("neutralScorer")).isZero();
        assertThat(stubs.invocations("findingProsecutor")).isZero();
        assertThat(stubs.invocations("reportWriter")).isEqualTo(1);

        // the scorer proposed APPROVE, but a MEDIUM finding sets the floor to COMMENT
        assertThat(report.verdict()).isEqualTo(Verdict.COMMENT);
        assertThat(report.verdictReasoning()).contains("Rule applied");
        assertThat(report.wrong()).extracting(Finding::id).containsExactly("F1");
        assertThat(report.improvements()).anyMatch(i -> i.startsWith("Minor: **F2"));
        assertThat(report.run().reviewersRun()).containsExactlyInAnyOrder("correctnessReviewer", "designReviewer");

        // only the compact routing input reaches the decision model
        assertThat(router.requests()).hasSize(1);
        assertThat(router.requests().get(0).input().toString()).contains("RoutingInput").doesNotContain("diff --git");

        Path dir = out.resolve("langchain4j-langchain4j-6610");
        assertThat(Files.readString(dir.resolve("report.md"))).contains("**Verdict:** COMMENT").contains("### Scores");
        assertThat(dir.resolve("report.json")).exists();
        assertThat(system.agentMonitor().successfulExecutions()).hasSize(1);
    }

    @Test
    void interactive_review_asks_questions_debates_and_revises_after_a_contest() {
        AtomicInteger judgements = new AtomicInteger();
        StubAgents stubs = StubAgents.defaults()
                .returning(PrTriage.class, scope -> StubAgents.summary(Category.BUGFIX, Risk.HIGH, true))
                .returning(CorrectnessReviewer.class, scope -> List.of(
                        raw(FindingCategory.CORRECTNESS, Severity.HIGH, MAIN_FILE, 130, 0.6)))
                // the first debate confirms the finding, the one requested by the reviewer rejects it
                .returning(DebateJudge.class, scope -> judgements.incrementAndGet() == 1
                        ? new DebateVerdict(DebateOutcome.CONFIRMED, null, "Real.")
                        : new DebateVerdict(DebateOutcome.REJECTED, null, "The guard makes it impossible."));
        ScriptedReviewerConsole console = new ScriptedReviewerConsole("Backward compatibility is not required.",
                new ApprovalDecision(Decision.CONTEST, "there is a guard in AiServices", List.of("F1"), List.of("F2")),
                ApprovalDecision.approved());

        ReviewReport report = review(stubs, new FakeDecisionModel(CORRECTNESS_AND_DESIGN), console,
                ReviewMode.INTERACTIVE, ReviewOptions.defaults(), new CostLedger());

        assertThat(stubs.invocations("contextGatherer")).isEqualTo(1);
        assertThat(console.questionsAsked).containsExactly(List.of("Should default methods ever call the model?"));
        assertThat(stubs.invocations("debateJudge")).isEqualTo(2);
        assertThat(stubs.invocations("findingProsecutor")).isEqualTo(4); // two rounds per debate, no convergence
        assertThat(stubs.invocations("scorer")).isEqualTo(2);
        assertThat(console.reportsShown).hasSize(2);
        // before the contest: the confirmed HIGH correctness finding forces REQUEST_CHANGES
        assertThat(console.reportsShown.get(0).verdict()).isEqualTo(Verdict.REQUEST_CHANGES);
        assertThat(console.reportsShown.get(0).wrong().get(0).verification()).isEqualTo(Verification.CONFIRMED);

        assertThat(report.verdict()).isEqualTo(Verdict.APPROVE);
        assertThat(report.wrong()).isEmpty();
        assertThat(report.run().revisionRounds()).isEqualTo(1);
        List<Finding> findings = scope().readState(Keys.Findings.class);
        assertThat(findings).extracting(Finding::verification)
                .containsExactly(Verification.REJECTED, Verification.DROPPED_BY_REVIEWER);
        assertThat(scope().readState(Keys.ReviewerGuidance.class))
                .contains("Backward compatibility is not required.")
                .contains("contested F1").contains("dropped F2").contains("there is a guard in AiServices");
    }

    @Test
    void borderline_score_card_is_settled_by_three_voters() {
        StubAgents stubs = StubAgents.defaults()
                .returning(CorrectnessReviewer.class, scope -> List.of())
                .returning(Scorer.class, scope -> StubAgents.card(7.0, Verdict.COMMENT));

        ReviewReport report = review(stubs, CORRECTNESS_AND_DESIGN);

        assertThat(stubs.invocations("neutralScorer")).isEqualTo(1);
        assertThat(stubs.invocations("skepticalScorer")).isEqualTo(1);
        assertThat(stubs.invocations("lenientScorer")).isEqualTo(1);
        assertThat(report.scores().overall()).isEqualTo(7.0); // mean of 7.0, 6.0 and 8.0
        assertThat(report.verdict()).isEqualTo(Verdict.COMMENT); // two COMMENT votes against one APPROVE
        assertThat(report.verdictReasoning()).contains("independent scorers");
    }

    @Test
    void clear_score_card_skips_the_vote() {
        StubAgents stubs = StubAgents.defaults().returning(CorrectnessReviewer.class, scope -> List.of());

        review(stubs, CORRECTNESS_AND_DESIGN);

        assertThat(stubs.invocations("neutralScorer")).isZero();
    }

    @Test
    void production_code_skipped_by_the_router_gets_a_correctness_review_from_the_floor() {
        StubAgents stubs = StubAgents.defaults();

        ReviewReport report = review(stubs, Map.of("designReviewer", 0.9));

        assertThat(stubs.invocations("correctnessReviewer")).isZero();
        assertThat(stubs.invocations("correctnessReviewerFallback")).isEqualTo(1);
        assertThat(stubs.invocations("securityReviewerFallback")).isZero();
        assertThat(report.run().reviewersRun()).containsExactly("designReviewer", "correctnessReviewerFallback");
        assertThat(report.improvements()).anyMatch(i -> i.startsWith("Minor:") && i.contains("CORRECTNESS"));
    }

    @Test
    void security_sensitive_code_skipped_by_the_router_gets_a_security_review_from_the_floor() {
        diffOverride = SQL_FIX;
        StubAgents stubs = StubAgents.defaults().returning(CorrectnessReviewer.class, scope -> List.of());

        ReviewReport report = review(stubs, Map.of("correctnessReviewer", 0.9));

        assertThat(stubs.invocations("correctnessReviewerFallback")).isZero();
        assertThat(stubs.invocations("securityReviewerFallback")).isEqualTo(1);
        assertThat(report.run().reviewersRun()).containsExactly("correctnessReviewer", "securityReviewerFallback");
    }

    @Test
    void documentation_only_change_gets_only_a_documentation_review() {
        diffOverride = README;
        StubAgents stubs = StubAgents.defaults()
                .returning(PrTriage.class, scope -> StubAgents.summary(Category.DOCS, Risk.LOW, false));

        ReviewReport report = review(stubs, Map.of());

        assertThat(report.run().reviewersRun()).containsExactly("docsApiReviewerFallback");
        assertThat(stubs.invocations("correctnessReviewerFallback")).isZero();
    }

    @Test
    void failing_decision_model_degrades_to_the_fallback_reviewer() {
        StubAgents stubs = StubAgents.defaults();

        ReviewReport report = review(stubs, FakeDecisionModel.failing(), null, ReviewMode.AUTO,
                ReviewOptions.defaults(), new CostLedger());

        assertThat(stubs.invocations("correctnessReviewerFallback")).isEqualTo(1);
        assertThat(report.limitations()).anyMatch(l -> l.contains("reviewPanel"));
    }

    @Test
    void failing_reviewer_is_disclosed_instead_of_failing_the_review() {
        StubAgents stubs = StubAgents.defaults().failing(CorrectnessReviewer.class);

        ReviewReport report = review(stubs, CORRECTNESS_AND_DESIGN);

        assertThat(report.limitations()).anyMatch(l -> l.contains("correctnessReviewer"));
        assertThat(report.scores().confidence()).isLessThanOrEqualTo(0.6);
        assertThat(report.improvements()).anyMatch(i -> i.startsWith("Minor: **F1"));
    }

    @Test
    void account_problems_fail_the_review_at_the_first_agent() {
        StubAgents stubs = StubAgents.defaults().returning(CorrectnessReviewer.class, scope -> {
            throw new dev.langchain4j.exception.InvalidRequestException(
                    "{\"type\":\"error\",\"error\":{\"message\":\"Your credit balance is too low to access the Anthropic API.\"}}");
        });

        assertThatThrownBy(() -> review(stubs, CORRECTNESS_AND_DESIGN)).hasStackTraceContaining("credit balance");
        assertThat(stubs.invocations("scorer")).isZero();
    }

    @Test
    void failing_scorer_fails_the_review() {
        StubAgents stubs = StubAgents.defaults().failing(Scorer.class);

        assertThatThrownBy(() -> review(stubs, CORRECTNESS_AND_DESIGN)).hasStackTraceContaining("scorer failed");
    }

    @Test
    void cost_cap_skips_debates_and_votes_but_completes_the_review() {
        CostLedger ledger = new CostLedger();
        ledger.record("earlier", "claude-opus-5-5", 100_000, 10_000, 0, 0); // $0.60
        ReviewOptions options = new ReviewOptions(false, 50, 60_000, null, 3, 0.5, 0.5, 4);
        StubAgents stubs = StubAgents.defaults()
                .returning(CorrectnessReviewer.class, scope -> List.of(
                        raw(FindingCategory.CORRECTNESS, Severity.HIGH, MAIN_FILE, 130, 0.6)))
                .returning(Scorer.class, scope -> StubAgents.card(7.0, Verdict.COMMENT));

        ReviewReport report = review(stubs, new FakeDecisionModel(CORRECTNESS_AND_DESIGN), null, ReviewMode.AUTO,
                options, ledger);

        assertThat(stubs.invocations("debateJudge")).isZero();
        assertThat(stubs.invocations("neutralScorer")).isZero();
        assertThat(report.wrong().get(0).verification()).isEqualTo(Verification.UNSURE);
        assertThat(report.limitations()).anyMatch(l -> l.contains("cost cap"));
        assertThat(ledger.capEvents()).anyMatch(e -> e.startsWith("debate of F1"))
                .anyMatch(e -> e.startsWith("score voting"));
    }

    @Test
    void very_large_diff_is_reviewed_in_chunks() {
        int files = DiffParser.parse(FixtureGitHubGateway.diff6610()).size();
        ReviewOptions options = new ReviewOptions(false, 2, 60_000, null, 3, 0.5, 2.0, 4);
        StubAgents stubs = StubAgents.defaults();

        ReviewReport report = review(stubs, new FakeDecisionModel(CORRECTNESS_AND_DESIGN), null, ReviewMode.AUTO,
                options, new CostLedger());

        List<String> chunks = scope().readState(Keys.DiffChunks.class);
        assertThat(files).isGreaterThan(2);
        assertThat(chunks).isNotEmpty();
        assertThat(stubs.invocations("chunkReviewer")).isEqualTo(chunks.size());
        assertThat(report.run().reviewersRun()).contains("chunkReviewer");
        assertThat(report.limitations()).anyMatch(l -> l.startsWith("Not every file was reviewed in full"));
        assertThat(report.scores().confidence()).isLessThanOrEqualTo(0.6);
    }

    @Test
    void failing_chunk_reviewer_keeps_the_findings_of_the_other_chunks() {
        // one file in the main diff, the rest chunked at 4K tokens: several chunks, one of which fails
        ReviewOptions options = new ReviewOptions(false, 1, 8_000, null, 3, 0.5, 2.0, 10);
        StubAgents stubs = StubAgents.defaults().failingChunkContaining("ToolErrorHandlingNoticeTest.java");

        ReviewReport report = review(stubs, new FakeDecisionModel(CORRECTNESS_AND_DESIGN), null, ReviewMode.AUTO,
                options, new CostLedger());

        List<String> chunks = scope().readState(Keys.DiffChunks.class);
        List<List<dev.langchain4j.prreviewer.model.RawFinding>> chunkFindings = scope().readState(Keys.ChunkFindings.class);
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunkFindings).hasSize(chunks.size());
        assertThat(chunkFindings.stream().filter(List::isEmpty)).hasSize(1);                 // only the failed chunk
        assertThat(stubs.invocations("chunkReviewer")).isEqualTo(chunks.size() - 1);
        assertThat(scope().readState(Keys.DegradedAgents.class)).singleElement().asString().startsWith("chunkReviewer_");
        assertThat(report.improvements()).anyMatch(i -> i.startsWith("Minor:") && i.contains("CORRECTNESS"));
    }

    @Test
    void rejected_report_is_not_written() {
        ScriptedReviewerConsole console = new ScriptedReviewerConsole("",
                new ApprovalDecision(Decision.REJECTED, "", List.of(), List.of()));

        review(StubAgents.defaults(), new FakeDecisionModel(CORRECTNESS_AND_DESIGN), console, ReviewMode.INTERACTIVE,
                ReviewOptions.defaults(), new CostLedger());

        assertThat(out.resolve("langchain4j-langchain4j-6610/report.md")).doesNotExist();
    }

    @Test
    void edit_notes_are_applied_by_the_finalizer() {
        StubAgents stubs = StubAgents.defaults();
        ScriptedReviewerConsole console = new ScriptedReviewerConsole("",
                new ApprovalDecision(Decision.EDIT, "mention the changelog", List.of(), List.of()));

        ReviewReport report = review(stubs, new FakeDecisionModel(CORRECTNESS_AND_DESIGN), console,
                ReviewMode.INTERACTIVE, ReviewOptions.defaults(), new CostLedger());

        assertThat(stubs.invocations("reportFinalizer")).isEqualTo(1);
        assertThat(report.summary()).endsWith("(edited by the reviewer)");
    }

    @Test
    void contest_after_the_last_revision_round_is_applied_without_a_debate() {
        StubAgents stubs = StubAgents.defaults();
        ApprovalDecision contest = new ApprovalDecision(Decision.CONTEST, "", List.of("F1"), List.of());
        ScriptedReviewerConsole console = new ScriptedReviewerConsole("", contest, contest,
                new ApprovalDecision(Decision.CONTEST, "F2 is irrelevant", List.of("F1"), List.of("F2")));

        ReviewReport report = review(stubs, new FakeDecisionModel(CORRECTNESS_AND_DESIGN), console,
                ReviewMode.INTERACTIVE, ReviewOptions.defaults(), new CostLedger());

        assertThat(console.reportsShown).hasSize(3);
        assertThat(stubs.invocations("debateJudge")).isEqualTo(2); // the third contest comes too late for a debate
        assertThat(stubs.invocations("reportFinalizer")).isEqualTo(1);
        assertThat(report.wrong()).extracting(Finding::verification).containsExactly(Verification.UNSURE);
        assertThat(report.improvements()).noneMatch(i -> i.contains("F2"));
        assertThat(report.limitations()).anyMatch(l -> l.contains("could not be debated"));
    }

    @Test
    void security_reviewer_findings_on_lines_outside_the_diff_are_discarded() {
        StubAgents stubs = StubAgents.defaults().returning(SecurityReviewer.class, scope -> List.of(
                raw(FindingCategory.SECURITY, Severity.CRITICAL, MAIN_FILE, 900, 0.9),
                raw(FindingCategory.SECURITY, Severity.CRITICAL, "src/main/java/Invented.java", 1, 0.9)));

        ReviewReport report = review(stubs, Map.of("correctnessReviewer", 0.9, "securityReviewer", 0.9));

        assertThat(report.wrong()).noneMatch(f -> f.category() == FindingCategory.SECURITY);
        assertThat(report.limitations()).anyMatch(l -> l.startsWith("2 findings were discarded"));
        assertThat(report.verdict()).isNotEqualTo(Verdict.REQUEST_CHANGES);
    }
}
