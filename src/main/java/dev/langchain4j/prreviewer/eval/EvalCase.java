package dev.langchain4j.prreviewer.eval;

import dev.langchain4j.prreviewer.model.Verdict;
import java.util.List;

/**
 * A pull request of the eval set with its labels, derived from what happened to it: the verdict the maintainers
 * effectively gave, and the review comments that led to follow-up commits, which a good review should also raise.
 */
public record EvalCase(
        String pr,
        String title,
        boolean merged,
        int files,
        int changedLines,
        Verdict expectedVerdict,
        List<MustFind> mustFind,
        String rationale) {

    /** A reviewer comment that the author addressed with a later commit. */
    public record MustFind(String file, int line, String comment) {}
}
