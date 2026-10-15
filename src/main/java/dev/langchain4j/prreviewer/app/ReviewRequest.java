package dev.langchain4j.prreviewer.app;

import dev.langchain4j.prreviewer.model.PrRef;
import dev.langchain4j.prreviewer.model.ReviewMode;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import java.nio.file.Path;

/**
 * What to review and how.
 *
 * @param outputRoot where the review's directory is created, or null for {@code reviewer.output-dir}
 */
public record ReviewRequest(PrRef pr, ReviewMode mode, ReviewOptions options, Path outputRoot) {

    public ReviewRequest(PrRef pr, ReviewMode mode, ReviewOptions options) {
        this(pr, mode, options, null);
    }
}
