package dev.langchain4j.prreviewer.hitl;

import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.PullRequest;
import dev.langchain4j.prreviewer.model.ReviewReport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Asks the reviewer on standard input and output. */
public final class StdinReviewerConsole implements ReviewerConsole {

    private final BufferedReader in;
    private final PrintStream out;
    private final boolean approveOnEof;

    public StdinReviewerConsole(boolean approveOnEof) {
        this(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)), System.out, approveOnEof);
    }

    public StdinReviewerConsole(BufferedReader in, PrintStream out, boolean approveOnEof) {
        this.in = in;
        this.out = out;
        this.approveOnEof = approveOnEof;
    }

    @Override
    public synchronized String askClarifications(PullRequest pr, PrSummary summary, List<String> questions) {
        out.println();
        out.println("== Clarifications for " + pr.ref() + ": " + pr.title());
        out.println(summary);
        StringBuilder guidance = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            out.println();
            out.println("Q" + (i + 1) + ". " + questions.get(i));
            String answer = readLine("A" + (i + 1) + " (empty to skip)> ");
            if (answer != null && !answer.isBlank()) {
                guidance.append("Q: ").append(questions.get(i)).append("\nA: ").append(answer.strip()).append('\n');
            }
        }
        String focus = readLine("Any focus areas or constraints the reviewers should know (empty for none)> ");
        if (focus != null && !focus.isBlank()) {
            guidance.append("Focus: ").append(focus.strip()).append('\n');
        }
        return guidance.toString().strip();
    }

    @Override
    public synchronized ApprovalDecision askApproval(ReviewReport report, List<Finding> findings, double spentUsd,
                                                     double capUsd, boolean debatesAffordable) {
        out.println();
        out.println("================ REVIEW REPORT ================");
        out.println(report.markdown());
        out.println("===============================================");
        out.printf(Locale.ROOT, "Spent so far: $%.2f of the $%.2f soft cap%n", spentUsd, capUsd);
        out.println("Answer with: approve | edit <notes> | contest <ids> [notes] | drop <ids> [notes] | reject [notes]");
        Set<String> ids = findings.stream().map(Finding::id).collect(Collectors.toSet());
        while (true) {
            String answer = readLine("decision> ");
            if (answer == null) {
                if (approveOnEof) {
                    out.println("(end of input: approving)");
                    return ApprovalDecision.approved();
                }
                throw new IllegalStateException("End of input while waiting for the approval decision; "
                        + "use --approve-on-eof to approve automatically");
            }
            Optional<ApprovalDecision> decision = ApprovalCommandParser.parse(answer, ids);
            if (decision.isEmpty()) {
                out.println("Not understood. Known findings: " + ids.stream().sorted().toList());
                continue;
            }
            if (decision.get().decision() == ApprovalDecision.Decision.CONTEST
                    && !decision.get().contestedFindingIds().isEmpty() && !debatesAffordable) {
                out.printf(Locale.ROOT, "The cost cap of $%.2f is reached ($%.2f spent): findings can no longer be "
                        + "debated. Use drop or edit instead.%n", capUsd, spentUsd);
                continue;
            }
            return decision.get();
        }
    }

    private String readLine(String prompt) {
        out.print(prompt);
        out.flush();
        try {
            return in.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
