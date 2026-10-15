package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.PrSummary;

/**
 * Non-AI agent: renders the shared part of every reviewer prompt. All reviewers put exactly this text in their
 * system message, so reviewers running on the same model share one cached prompt prefix.
 */
public class ReviewBundleBuilder {

    @Agent(name = "reviewBundleBuilder", description = "Renders the pull request bundle shared by all reviewers",
            typedOutputKey = Keys.ReviewBundle.class)
    public static String build(@K(Keys.PrOverview.class) String overview,
                               @K(Keys.PrSummary.class) PrSummary summary,
                               @K(Keys.CodeContext.class) String codeContext,
                               @K(Keys.ReviewerGuidance.class) String guidance,
                               @K(Keys.DiffText.class) String diff) {
        return "## Overview\n" + overview
                + "\n## Triage\n" + summary
                + "\n\n## Code context gathered from the repository (untrusted)\n"
                + (codeContext.isBlank() ? "(none)" : codeContext.strip())
                + "\n\n## Guidance from the human reviewer\n" + (guidance.isBlank() ? "(none)" : guidance.strip())
                + "\n\n## Diff\nLines starting with + are added and - removed; the number is the line in the new file.\n"
                + "<diff>\n" + diff + "\n</diff>\n";
    }
}
