package dev.langchain4j.prreviewer.config;

import dev.langchain4j.prreviewer.model.ReviewOptions;
import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** The review options built from the configured defaults. */
@ApplicationScoped
@Unremovable
public class ReviewDefaults {

    @Inject
    ReviewerConfig config;

    public static ReviewDefaults get() {
        return Arc.container().instance(ReviewDefaults.class).get();
    }

    public ReviewOptions options() {
        return new ReviewOptions(config.deep(), config.maxFiles(), config.maxDiffTokens(), config.modelTier().orElse(null),
                config.maxDebates(), config.routerThreshold().orElse(config.decisionModel().defaultRouterThreshold()),
                config.maxCost(), config.maxChunks(), config.provider(), config.decisionModel());
    }

    public ReviewerConfig config() {
        return config;
    }
}
