package dev.langchain4j.prreviewer.github;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import dev.langchain4j.prreviewer.model.DiffStats;
import dev.langchain4j.prreviewer.model.PullRequest;
import dev.langchain4j.prreviewer.model.ReviewOptions;

/** Non-AI agent: parses and classifies the diff, renders the reviewed diff, its chunks and the overview. */
public class DiffPreprocessor {

    @Agent(name = "diffPreprocessor",
            description = "Parses the diff, renders the reviewed part within the size limits and computes statistics",
            outputKey = Keys.DIFF_STATS)
    public static DiffStats preprocess(AgenticScope scope, PullRequest pullRequest, ReviewOptions options) {
        DiffPreprocessing.Result result = DiffPreprocessing.process(pullRequest, options);
        scope.writeState(Keys.CHANGES, result.changes());
        scope.writeState(Keys.DIFF_TEXT, result.diffText());
        scope.writeState(Keys.DIFF_CHUNKS, result.chunks());
        scope.writeState(Keys.PR_OVERVIEW, DiffPreprocessing.overview(pullRequest, result.stats()));
        if (!result.stats().skippedFiles().isEmpty()) {
            ScopeState.append(scope, Keys.LIMITATIONS, "Not reviewed, generated or binary: "
                    + String.join(", ", result.stats().skippedFiles()) + ".");
        }
        if (result.stats().truncated()) {
            ScopeState.append(scope, Keys.LIMITATIONS, "Not every file was reviewed in full: "
                    + String.join(", ", result.stats().omittedFiles())
                    + (result.chunks().isEmpty() ? "" : " (" + result.chunks().size()
                            + " chunks of left-out files got a separate correctness and security pass)"));
        }
        return result.stats();
    }
}
