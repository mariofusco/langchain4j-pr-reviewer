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

    /** The model of the local provider {@code jitllm} ({@code reviewer.jitllm.*}). */
    Jitllm jitllm();

    interface Jitllm {
        /** The GGUF file of every tier without its own; empty: the provider is not offered. */
        Optional<Path> modelPath();

        /** The model of the small tier (triage, checklists, critic); empty: {@link #modelPath()}. */
        Optional<Path> economyModelPath();

        /** The model of the medium tier (reviewers, scoring, writing); empty: {@link #modelPath()}. */
        Optional<Path> standardModelPath();

        /** The model of the large tier (escalations only); empty: {@link #modelPath()}. */
        Optional<Path> premiumModelPath();

        /** The context window in tokens, which sizes the key/value cache on the GPU. */
        @WithDefault("32768")
        int contextLength();

        /** The output budget of one call, thinking included. */
        @WithDefault("8192")
        int maxTokens();

        @WithDefault("0.6")
        double temperature();

        @WithDefault("0.95")
        double topP();

        /** Whether reasoning models think before answering. */
        @WithDefault("true")
        boolean think();

        /**
         * Whether the premium tier's model thinks; empty: {@link #think()}. A large model on a laptop GPU decodes a
         * few tokens a second, and thousands of thinking tokens make one escalation take a quarter of an hour.
         */
        Optional<Boolean> premiumThink();

        /** On the GPU through TornadoVM; false runs jitllm's CPU path. */
        @WithDefault("true")
        boolean gpu();
    }
}
