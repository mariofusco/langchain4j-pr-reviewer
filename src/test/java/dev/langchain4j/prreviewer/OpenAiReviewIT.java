package dev.langchain4j.prreviewer;

import dev.langchain4j.prreviewer.models.ChatModelProvider;
import org.junit.jupiter.api.condition.EnabledIf;

/** The same review on the OpenAI tiers (gpt-5-nano, gpt-5.6-luna, gpt-5.1); a few cents per run. */
@EnabledIf("dev.langchain4j.prreviewer.support.Availability#openAi")
class OpenAiReviewIT extends RealModelReviewIT {

    @Override
    protected ChatModelProvider provider() {
        return ChatModelProvider.OPENAI;
    }

    @Override
    protected double costCapUsd() {
        return 0.5;
    }
}
