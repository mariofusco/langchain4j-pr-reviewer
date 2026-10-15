package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.prreviewer.model.DiffStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The reviewer coverage floor: whatever the router decides, production code gets a correctness review,
 * security-sensitive code a security review and documentation-only changes a documentation review. The router owns
 * the judgment on everything else; this floor only protects against a decision model that under-selects.
 */
public final class ReviewerCoverage {

    private ReviewerCoverage() {}

    public static boolean correctnessMissing(Map<String, Object> panel, DiffStats stats) {
        return stats.productionCodeChanged() && !panel.containsKey("correctnessReviewer");
    }

    public static boolean securityMissing(Map<String, Object> panel, DiffStats stats) {
        return stats.touchesSecuritySensitive() && !panel.containsKey("securityReviewer");
    }

    public static boolean docsMissing(Map<String, Object> panel, DiffStats stats) {
        return stats.documentationOnly() && !panel.containsKey("docsApiReviewer");
    }

    /** The fallback reviewers the floor runs for this panel. */
    public static List<String> floorReviewers(Map<String, Object> panel, DiffStats stats) {
        List<String> names = new ArrayList<>();
        if (correctnessMissing(panel, stats)) {
            names.add("correctnessReviewerFallback");
        }
        if (securityMissing(panel, stats)) {
            names.add("securityReviewerFallback");
        }
        if (docsMissing(panel, stats)) {
            names.add("docsApiReviewerFallback");
        }
        return names;
    }

    /** The name of the reviewer whose findings are under the key of the given panel reviewer. */
    public static String reviewerOf(String panelReviewer, Map<String, Object> panel) {
        return panel.containsKey(panelReviewer) ? panelReviewer : panelReviewer + "Fallback";
    }
}
