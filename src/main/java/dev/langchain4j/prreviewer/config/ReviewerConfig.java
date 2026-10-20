package dev.langchain4j.prreviewer.config;

import dev.langchain4j.prreviewer.model.DecisionProvider;
import dev.langchain4j.prreviewer.model.ModelProvider;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.ReviewMode;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.nio.file.Path;
import java.util.Optional;

/** Defaults of the review options ({@code reviewer.*}); CLI flags and the web form override them per review. */
@ConfigMapping(prefix = "reviewer")
public interface ReviewerConfig {

    @WithDefault("anthropic")
    ModelProvider provider();

    @WithDefault("local")
    DecisionProvider decisionModel();

    /** Empty: the decision model's default threshold. */
    Optional<Double> routerThreshold();

    @WithDefault("auto")
    ReviewMode mode();

    @WithDefault("false")
    boolean deep();

    Optional<ModelTier> modelTier();

    @WithDefault("2.0")
    double maxCost();

    @WithDefault("3")
    int maxDebates();

    @WithDefault("50")
    int maxFiles();

    @WithDefault("60000")
    int maxDiffTokens();

    @WithDefault("4")
    int maxChunks();

    @WithDefault("review-output")
    Path outputDir();
}
