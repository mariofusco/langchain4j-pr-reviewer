package dev.langchain4j.prreviewer.github;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeLists;
import dev.langchain4j.prreviewer.model.DiffStats;
import dev.langchain4j.prreviewer.model.PullRequest;
import dev.langchain4j.prreviewer.model.ReviewOptions;

/** Non-AI agent: parses and classifies the diff, renders the reviewed diff, its chunks and the overview. */
public class DiffPreprocessor {

    @Agent(name = "diffPreprocessor",
            description = "Parses the diff, renders the reviewed part within the size limits and computes statistics",
            typedOutputKey = Keys.DiffStats.class)
    public static DiffStats preprocess(AgenticScope scope,
                                       @K(Keys.PullRequest.class) PullRequest pr,
                                       @K(Keys.Options.class) ReviewOptions options) {
        DiffPreprocessing.Result result = DiffPreprocessing.process(pr, options);
        scope.writeState(Keys.Changes.class, result.changes());
        scope.writeState(Keys.DiffText.class, result.diffText());
        scope.writeState(Keys.DiffChunks.class, result.chunks());
        scope.writeState(Keys.PrOverview.class, DiffPreprocessing.overview(pr, result.stats()));
        if (!result.stats().skippedFiles().isEmpty()) {
            ScopeLists.append(scope, Keys.Limitations.class, "Not reviewed, generated or binary: "
                    + String.join(", ", result.stats().skippedFiles()) + ".");
        }
        if (result.stats().truncated()) {
            ScopeLists.append(scope, Keys.Limitations.class, "Not every file was reviewed in full: "
                    + String.join(", ", result.stats().omittedFiles())
                    + (result.chunks().isEmpty() ? "" : " (" + result.chunks().size()
                            + " chunks of left-out files got a separate correctness and security pass)"));
        }
        return result.stats();
    }
}
