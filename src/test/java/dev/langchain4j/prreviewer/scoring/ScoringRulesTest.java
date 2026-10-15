package dev.langchain4j.prreviewer.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.prreviewer.model.Category;
import dev.langchain4j.prreviewer.model.DiffStats;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.FindingCategory;
import dev.langchain4j.prreviewer.model.Metric;
import dev.langchain4j.prreviewer.model.MetricScore;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.Risk;
import dev.langchain4j.prreviewer.model.ScoreCard;
import dev.langchain4j.prreviewer.model.Severity;
import dev.langchain4j.prreviewer.model.Size;
import dev.langchain4j.prreviewer.model.Verdict;
import dev.langchain4j.prreviewer.model.Verification;
import dev.langchain4j.prreviewer.support.StubAgents;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScoringRulesTest {

    static final DiffStats WITH_TESTS = stats(true);
    static final PrSummary BUGFIX = new PrSummary("fix", Category.BUGFIX, Size.SMALL, Risk.MEDIUM, List.of(), false, List.of());

    static DiffStats stats(boolean tests) {
        return new DiffStats(1, 10, 2, List.of("java"), tests, true, false, false, false, false, false, 100, List.of(), List.of());
    }

    static Finding finding(FindingCategory category, Severity severity, double confidence, Verification verification) {
        return new Finding("F1", "r", category, severity, "A.java", 3, "t", "e", "s", "x", confidence, verification);
    }

    @ParameterizedTest
    @CsvSource({
        "SECURITY,CRITICAL,0.9,UNVERIFIED,SECURITY,2.0",
        "SECURITY,HIGH,0.9,UNVERIFIED,SECURITY,5.0",
        "CORRECTNESS,CRITICAL,0.5,CONFIRMED,CORRECTNESS,3.0",
        "CORRECTNESS,HIGH,0.7,UNVERIFIED,CORRECTNESS,5.0",
    })
    void credible_severe_findings_cap_their_metric(FindingCategory category, Severity severity, double confidence,
                                                  Verification verification, Metric metric, double cap) {
        Caps.Result result = Caps.apply(StubAgents.card(9.0, Verdict.APPROVE).metrics(),
                List.of(finding(category, severity, confidence, verification)), WITH_TESTS, BUGFIX);

        assertThat(score(result, metric)).isEqualTo(cap);
        assertThat(result.appliedRules()).singleElement().asString().contains(metric.label());
    }

    @Test
    void unconfirmed_low_confidence_rejected_and_unsure_findings_do_not_cap_or_floor() {
        Finding unsure = finding(FindingCategory.CORRECTNESS, Severity.HIGH, 0.95, Verification.UNSURE);
        Caps.Result result = Caps.apply(StubAgents.card(9.0, Verdict.APPROVE).metrics(), List.of(
                        finding(FindingCategory.SECURITY, Severity.CRITICAL, 0.5, Verification.UNVERIFIED),
                        finding(FindingCategory.CORRECTNESS, Severity.CRITICAL, 0.9, Verification.REJECTED),
                        unsure),
                WITH_TESTS, BUGFIX);

        assertThat(score(result, Metric.SECURITY)).isEqualTo(9.0);
        assertThat(score(result, Metric.CORRECTNESS)).isEqualTo(9.0);
        // an unsettled HIGH finding is still a notable finding, so the floor is COMMENT, not REQUEST_CHANGES
        assertThat(VerdictRules.floor(List.of(unsure)).verdict()).isEqualTo(Verdict.COMMENT);
    }

    @Test
    void caps_never_raise_a_score_and_fix_without_tests_is_capped() {
        Caps.Result result = Caps.apply(StubAgents.card(1.0, Verdict.REQUEST_CHANGES).metrics(),
                List.of(finding(FindingCategory.SECURITY, Severity.HIGH, 0.9, Verification.CONFIRMED)), stats(false), BUGFIX);

        assertThat(score(result, Metric.SECURITY)).isEqualTo(1.0);
        assertThat(score(result, Metric.TEST_COVERAGE)).isEqualTo(1.0);
        assertThat(Caps.apply(StubAgents.card(9.0, Verdict.APPROVE).metrics(), List.of(), stats(false), BUGFIX)
                .metrics()).filteredOn(m -> m.metric() == Metric.TEST_COVERAGE).singleElement()
                .extracting(MetricScore::score).isEqualTo(4.0);
    }

    @Test
    void overall_is_the_renormalized_weighted_mean_of_applicable_metrics() {
        List<MetricScore> metrics = new ArrayList<>();
        for (Metric m : Metric.values()) {
            boolean applicable = m != Metric.API_AND_DOCS && m != Metric.TEST_COVERAGE;
            metrics.add(new MetricScore(m, applicable, m == Metric.CORRECTNESS ? 4.0 : 8.0, ""));
        }
        // weights 0.20, 0.30, 0.10, 0.15, 0.10 over 0.85: (0.20*8 + 0.30*4 + 0.10*8 + 0.15*8 + 0.10*8) / 0.85
        assertThat(Caps.overall(metrics)).isEqualTo(6.6);
    }

    @Test
    void missing_metrics_are_not_applicable_and_out_of_range_scores_are_clamped() {
        Caps.Result result = Caps.apply(List.of(new MetricScore(Metric.USEFULNESS, true, 14.0, "")), List.of(),
                WITH_TESTS, BUGFIX);

        assertThat(result.metrics()).hasSize(Metric.values().length);
        assertThat(score(result, Metric.USEFULNESS)).isEqualTo(10.0);
        assertThat(result.metrics()).filteredOn(m -> m.metric() == Metric.SECURITY).singleElement()
                .extracting(MetricScore::applicable).isEqualTo(false);
    }

    @Test
    void verdict_floor_follows_the_findings_and_never_relaxes_the_proposal() {
        Finding high = finding(FindingCategory.CORRECTNESS, Severity.HIGH, 0.9, Verification.UNVERIFIED);
        Finding medium = finding(FindingCategory.DESIGN, Severity.MEDIUM, 0.3, Verification.UNVERIFIED);
        Finding highDesign = finding(FindingCategory.DESIGN, Severity.HIGH, 0.9, Verification.UNVERIFIED);

        assertThat(VerdictRules.floor(List.of(high)).verdict()).isEqualTo(Verdict.REQUEST_CHANGES);
        assertThat(VerdictRules.floor(List.of(medium)).verdict()).isEqualTo(Verdict.COMMENT);
        assertThat(VerdictRules.floor(List.of(highDesign)).verdict()).isEqualTo(Verdict.COMMENT);
        assertThat(VerdictRules.floor(List.of()).verdict()).isEqualTo(Verdict.APPROVE);

        assertThat(VerdictRules.apply(Verdict.APPROVE, VerdictRules.floor(List.of(high)))).isEqualTo(Verdict.REQUEST_CHANGES);
        assertThat(VerdictRules.apply(Verdict.REQUEST_CHANGES, VerdictRules.floor(List.of()))).isEqualTo(Verdict.REQUEST_CHANGES);
        VerdictRules.Floor floor = VerdictRules.floor(List.of(high));
        assertThat(VerdictRules.explain("Looks fine.", Verdict.APPROVE, floor, Verdict.REQUEST_CHANGES))
                .isEqualTo("Looks fine. Rule applied: HIGH correctness finding F1, so the verdict is at least REQUEST_CHANGES.");
    }

    @Test
    void voting_is_triggered_only_by_borderline_cards() {
        Finding doubtful = finding(FindingCategory.CORRECTNESS, Severity.HIGH, 0.75, Verification.UNVERIFIED);
        Finding certain = finding(FindingCategory.CORRECTNESS, Severity.HIGH, 0.95, Verification.CONFIRMED);

        assertThat(VotingTrigger.isBorderline(StubAgents.card(7.3, Verdict.APPROVE), List.of(), WITH_TESTS, BUGFIX)).isTrue();
        assertThat(VotingTrigger.isBorderline(StubAgents.card(9.0, Verdict.APPROVE), List.of(), WITH_TESTS, BUGFIX)).isFalse();
        // the verdict hangs on one finding of middling confidence
        assertThat(VotingTrigger.isBorderline(StubAgents.card(9.0, Verdict.COMMENT), List.of(doubtful), WITH_TESTS, BUGFIX)).isTrue();
        assertThat(VotingTrigger.isBorderline(StubAgents.card(9.0, Verdict.COMMENT), List.of(certain), WITH_TESTS, BUGFIX)).isFalse();
        // a model-only REQUEST_CHANGES
        assertThat(VotingTrigger.isBorderline(StubAgents.card(4.0, Verdict.REQUEST_CHANGES), List.of(), WITH_TESTS, BUGFIX)).isTrue();
    }

    @Test
    void votes_are_averaged_and_the_majority_verdict_wins_with_ties_to_the_stricter() {
        ScoreCard a = StubAgents.card(6.0, Verdict.COMMENT);
        ScoreCard b = StubAgents.card(8.0, Verdict.APPROVE);
        ScoreCard c = StubAgents.card(7.0, Verdict.COMMENT);

        ScoreCard result = ScoreCardVotingStrategy.aggregate(List.of(a, b, c));
        assertThat(result.metrics()).allSatisfy(m -> assertThat(m.score()).isEqualTo(7.0));
        assertThat(result.proposedVerdict()).isEqualTo(Verdict.COMMENT);

        ScoreCard tie = ScoreCardVotingStrategy.aggregate(List.of(a, b));
        assertThat(tie.proposedVerdict()).isEqualTo(Verdict.COMMENT);
        assertThat(ScoreCardVotingStrategy.aggregate(java.util.Arrays.asList(a, null, "junk"))).isNotNull();
    }

    @Test
    void a_metric_is_applicable_when_most_voters_say_so() {
        List<MetricScore> notApplicable = new ArrayList<>(StubAgents.card(5.0, Verdict.COMMENT).metrics());
        notApplicable.replaceAll(m -> m.metric() == Metric.API_AND_DOCS ? new MetricScore(m.metric(), false, 0, "n/a") : m);
        ScoreCard na = new ScoreCard(notApplicable, Verdict.COMMENT, "", 0.8);

        ScoreCard result = ScoreCardVotingStrategy.aggregate(List.of(na, na, StubAgents.card(9.0, Verdict.APPROVE)));

        assertThat(result.metrics()).filteredOn(m -> m.metric() == Metric.API_AND_DOCS).singleElement()
                .extracting(MetricScore::applicable).isEqualTo(false);
    }

    private static double score(Caps.Result result, Metric metric) {
        return result.metrics().stream().filter(m -> m.metric() == metric).findFirst().orElseThrow().score();
    }
}
