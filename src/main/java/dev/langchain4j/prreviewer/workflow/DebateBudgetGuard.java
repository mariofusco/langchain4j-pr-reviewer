package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.AppContext;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeLists;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.Verification;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Non-AI agent run after the debates: findings still queued were not debated, because the soft cost cap or the debate
 * limit was reached. They are marked unsure, and the skip is reported.
 */
public class DebateBudgetGuard {

    @Agent(name = "debateBudgetGuard", description = "Marks as unsure the contested findings left undebated by the cost cap",
            typedOutputKey = Keys.Findings.class)
    public static List<Finding> guard(AgenticScope scope,
                                      @K(Keys.Findings.class) List<Finding> findings,
                                      @K(Keys.ContestedFindings.class) List<Finding> queue) {
        if (queue.isEmpty()) {
            return findings;
        }
        Set<String> ids = queue.stream().map(Finding::id).collect(Collectors.toSet());
        String list = queue.stream().map(Finding::id).sorted().collect(Collectors.joining(", "));
        ReviewOptions options = scope.readState(Keys.Options.class);
        if (options != null && AppContext.get().ledger().exceeded(options.maxCostUsd())) {
            AppContext.get().ledger().noteCapSkip("debate of " + list);
            ScopeLists.append(scope, Keys.Limitations.class,
                    "Findings " + list + " were not debated because the cost cap was reached.");
        } else {
            ScopeLists.append(scope, Keys.Limitations.class,
                    "Findings " + list + " were not debated because the debate limit was reached.");
        }
        scope.writeState(Keys.ContestedFindings.class, List.of());
        return findings.stream()
                .map(f -> ids.contains(f.id()) && f.active() ? f.withVerification(Verification.UNSURE) : f)
                .toList();
    }
}
