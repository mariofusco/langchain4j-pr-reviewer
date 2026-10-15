package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.DebateVerdict;
import dev.langchain4j.prreviewer.model.Finding;
import java.util.List;

/** Non-AI agent: records the judge's decision on the debated finding; the judge may lower the severity, not raise it. */
public class ApplyDebateVerdict {

    @Agent(name = "applyDebateVerdict", description = "Records the outcome of a debate on its finding",
            outputKey = Keys.FINDINGS)
    public static List<Finding> apply(Finding contestedFinding, DebateVerdict debateVerdict, List<Finding> findings) {
        return findings.stream()
                .map(f -> f.id().equals(contestedFinding.id()) ? applyTo(f, debateVerdict) : f)
                .toList();
    }

    static Finding applyTo(Finding finding, DebateVerdict verdict) {
        Finding updated = finding.withVerification(verdict.outcome().toVerification());
        if (verdict.adjustedSeverity() != null && finding.severity().atLeast(verdict.adjustedSeverity())) {
            updated = updated.withSeverity(verdict.adjustedSeverity());
        }
        return updated;
    }
}
