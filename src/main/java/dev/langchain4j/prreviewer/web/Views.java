package dev.langchain4j.prreviewer.web;

import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import java.util.List;

/** What the pages show. */
final class Views {

    private Views() {}

    /** A choice of a select box. */
    record Choice(String value, String label, boolean selected) {}

    record IndexPage(String pr, String error, List<Choice> modes, List<Choice> providers, List<Choice> decisionModels,
                     List<Choice> tiers, ReviewOptions defaults, String routerThreshold, List<WebRun> runs,
                     List<ReportHistory.Entry> history) {}

    record RunPage(WebRun run, InteractionView interaction) {}

    /** A finding the reviewer can contest or drop. */
    record FindingRow(String id, String severity, String category, String title, String location, String verification) {

        static FindingRow of(Finding f) {
            return new FindingRow(f.id(), f.severity().name(), f.category().name(), f.title(), f.location(),
                    f.verification().name());
        }
    }

    /**
     * What the run waits for: {@code kind} is {@code none}, {@code clarification} or {@code approval}.
     */
    record InteractionView(String runId, String kind, List<String> questions, String reportHtml,
                           List<FindingRow> findings, String spent, String cap, boolean debatesAffordable, String error) {

        static InteractionView none(String runId) {
            return new InteractionView(runId, "none", List.of(), "", List.of(), "", "", true, null);
        }
    }

    record ReportPage(String directory, String pr, String verdict, String overall, String confidence, String cost,
                      String html, boolean hasTrace) {}
}
