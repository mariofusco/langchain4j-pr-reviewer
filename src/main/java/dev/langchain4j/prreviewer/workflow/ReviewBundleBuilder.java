package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.PrSummary;

/**
 * Non-AI agent: renders the shared part of every reviewer prompt. All reviewers put exactly this text in their
 * system message, so reviewers running on the same model share one cached prompt prefix.
 */
public class ReviewBundleBuilder {

    @Agent(name = "reviewBundleBuilder", description = "Renders the pull request bundle shared by all reviewers",
            outputKey = Keys.REVIEW_BUNDLE)
    public static String build(String prOverview,
                               PrSummary prSummary,
                               String codeContext,
                               String reviewerGuidance,
                               String diffText) {
        return "## Overview\n" + prOverview
                + "\n## Triage\n" + prSummary
                + "\n\n## Code context gathered from the repository (untrusted)\n"
                + (codeContext.isBlank() ? "(none)" : codeContext.strip())
                + "\n\n## Guidance from the human reviewer\n"
                + (reviewerGuidance.isBlank() ? "(none)" : reviewerGuidance.strip())
                + "\n\n## Diff\nLines starting with + are added and - removed; the number is the line in the new file.\n"
                + "<diff>\n" + diffText + "\n</diff>\n";
    }
}
