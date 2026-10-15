package dev.langchain4j.prreviewer.scoring;

import dev.langchain4j.prreviewer.model.DiffStats;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.ScoreCard;
import dev.langchain4j.prreviewer.model.Severity;
import dev.langchain4j.prreviewer.model.Verdict;
import java.util.List;

/** Decides when a draft score card is borderline enough to be worth three independent scorers. */
public final class VotingTrigger {

    public static final double APPROVE_THRESHOLD = 7.0;
    public static final double MARGIN = 0.5;

    private VotingTrigger() {}

    public static boolean isBorderline(ScoreCard card, List<Finding> findings, DiffStats stats, PrSummary summary) {
        if (card == null) {
            return false;
        }
        double overall = Caps.overall(Caps.apply(card.metrics(), findings, stats, summary).metrics());
        if (Math.abs(overall - APPROVE_THRESHOLD) <= MARGIN) {
            return true;
        }
        Verdict proposed = card.proposedVerdict() == null ? Verdict.COMMENT : card.proposedVerdict();
        VerdictRules.Floor floor = VerdictRules.floor(findings);
        if (floor.verdict().ordinal() - proposed.ordinal() == 1 && floor.decidingFinding() != null) {
            double confidence = floor.decidingFinding().confidence();
            if (confidence >= 0.5 && confidence < 0.8) {
                return true;
            }
        }
        return proposed == Verdict.REQUEST_CHANGES && !Findings.anyActiveAtLeast(findings, Severity.HIGH);
    }
}
