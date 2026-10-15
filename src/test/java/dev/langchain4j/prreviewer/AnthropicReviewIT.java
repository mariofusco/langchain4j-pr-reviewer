package dev.langchain4j.prreviewer;

import dev.langchain4j.prreviewer.models.ChatModelProvider;
import org.junit.jupiter.api.condition.EnabledIf;

@EnabledIf("dev.langchain4j.prreviewer.support.Availability#anthropic")
class AnthropicReviewIT extends RealModelReviewIT {

    @Override
    protected ChatModelProvider provider() {
        return ChatModelProvider.ANTHROPIC;
    }

    @Override
    protected double costCapUsd() {
        return 1.0;
    }
}
