package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.MetricScore;
import dev.langchain4j.prreviewer.model.PullRequest;
import dev.langchain4j.prreviewer.model.ReportDraft;
import dev.langchain4j.prreviewer.model.ReviewReport;
import dev.langchain4j.prreviewer.model.RunInfo;
import dev.langchain4j.prreviewer.model.Scores;
import dev.langchain4j.prreviewer.model.Severity;
import dev.langchain4j.prreviewer.model.Verdict;
import dev.langchain4j.prreviewer.model.Verification;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders the report. The prose comes from the writer; the findings, scores and verdict come from the data, so every
 * finding listed under "What is wrong" is a real, validated finding, and every CRITICAL, HIGH or MEDIUM finding is
 * listed even if the writer left it out.
 */
public final class ReportRenderer {

    private ReportRenderer() {}

    public static ReviewReport render(PullRequest pr, ReportDraft draft, List<Finding> active, Scores scores,
                                      Verdict verdict, String verdictReasoning, List<String> limitations, RunInfo run) {
        ReportDraft prose = draft == null ? ReportDraft.none() : draft;
        List<Finding> wrong = wrong(prose, active);
        List<Finding> minor = active.stream().filter(f -> !wrong.contains(f)).toList();

        StringBuilder md = new StringBuilder();
        md.append("## Review of ").append(pr.ref()).append(": ").append(oneLine(pr.title())).append("\n\n");
        md.append(String.format(Locale.ROOT, "**Verdict:** %s · **Overall:** %.1f/10 · **Confidence:** %.1f%n%n",
                verdict, scores.overall(), scores.confidence()));
        if (verdictReasoning != null && !verdictReasoning.isBlank()) {
            md.append("_Why: ").append(oneLine(verdictReasoning)).append("_\n\n");
        }
        md.append(String.format(Locale.ROOT, "_Mode: %s · Reviewers: %s · Revision rounds: %d · Cost: $%.2f_%n",
                run.mode(), run.reviewersRun().isEmpty() ? "none" : String.join(", ", run.reviewersRun()),
                run.revisionRounds(), run.costUsd()));

        md.append("\n### Summary\n\n")
                .append(prose.summary() == null || prose.summary().isBlank() ? "_No summary._" : prose.summary().strip())
                .append('\n');

        md.append("\n### What is good\n\n");
        bullets(md, prose.good(), "_Nothing specific._");

        md.append("\n### What is wrong\n\n");
        if (wrong.isEmpty()) {
            md.append("_No significant issue found._\n");
        } else {
            wrong.forEach(f -> md.append("- ").append(finding(f)).append('\n'));
        }

        md.append("\n### What could be improved\n\n");
        List<String> improvements = new ArrayList<>(prose.improvements() == null ? List.of() : prose.improvements());
        minor.forEach(f -> improvements.add("Minor: " + finding(f)));
        bullets(md, improvements, "_Nothing more._");

        md.append("\n### Scores\n\n| Metric | Score | Rationale |\n|---|---|---|\n");
        for (MetricScore m : scores.metrics()) {
            md.append("| ").append(m.metric().label()).append(" | ")
                    .append(m.applicable() ? String.format(Locale.ROOT, "%.1f", m.score()) : "n/a").append(" | ")
                    .append(cell(m.rationale())).append(" |\n");
        }
        md.append(String.format(Locale.ROOT, "| **Overall** | **%.1f** | weighted mean of the applicable metrics |%n",
                scores.overall()));

        md.append("\n### Limitations\n\n");
        bullets(md, limitations, "_None known._");

        if (!run.modelsByAgent().isEmpty()) {
            md.append("\n<details><summary>Models used</summary>\n\n");
            for (Map.Entry<String, String> e : run.modelsByAgent().entrySet()) {
                md.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
            md.append("\n</details>\n");
        }

        return new ReviewReport(pr.ref().toString(), pr.title(),
                prose.summary() == null ? "" : prose.summary().strip(),
                prose.good() == null ? List.of() : List.copyOf(prose.good()),
                wrong, List.copyOf(improvements), scores, verdict, verdictReasoning, scores.confidence(),
                List.copyOf(limitations), run, md.toString());
    }

    /** The writer's choice of findings, in its order, completed with every unlisted finding of at least MEDIUM. */
    static List<Finding> wrong(ReportDraft draft, List<Finding> active) {
        Set<Finding> wrong = new LinkedHashSet<>();
        if (draft.wrongFindingIds() != null) {
            for (String id : draft.wrongFindingIds()) {
                active.stream().filter(f -> f.id().equalsIgnoreCase(id.strip())).findFirst().ifPresent(wrong::add);
            }
        }
        active.stream().filter(f -> f.severity().atLeast(Severity.MEDIUM)).forEach(wrong::add);
        return List.copyOf(wrong);
    }

    static String finding(Finding f) {
        StringBuilder sb = new StringBuilder()
                .append("**").append(f.id()).append(" [").append(f.severity()).append("][").append(f.category())
                .append("]** `").append(f.location()).append("` - ").append(oneLine(f.title()));
        if (f.verification() == Verification.CONFIRMED) {
            sb.append(" (confirmed by debate)");
        } else if (f.verification() == Verification.UNSURE) {
            sb.append(" (unconfirmed)");
        }
        sb.append(". ").append(oneLine(f.explanation()));
        if (f.suggestion() != null && !f.suggestion().isBlank()) {
            sb.append(" _Suggestion:_ ").append(oneLine(f.suggestion()));
        }
        return sb.toString();
    }

    private static void bullets(StringBuilder md, List<String> items, String empty) {
        if (items == null || items.isEmpty()) {
            md.append(empty).append('\n');
            return;
        }
        items.forEach(item -> md.append("- ").append(item.strip()).append('\n'));
    }

    private static String cell(String text) {
        return oneLine(text).replace("|", "\\|");
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s*\\R\\s*", " ");
    }
}
