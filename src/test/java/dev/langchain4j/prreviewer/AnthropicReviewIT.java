package dev.langchain4j.prreviewer;

import dev.langchain4j.prreviewer.model.ModelProvider;
import dev.langchain4j.prreviewer.support.TestProfiles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.condition.EnabledIf;

/** The review on the Anthropic tiers; needs an account with credits. */
@QuarkusTest
@TestProfile(TestProfiles.RealServices.class)
@EnabledIf("dev.langchain4j.prreviewer.support.Availability#anthropic")
class AnthropicReviewIT extends RealModelReviewIT {

    @Override
    protected ModelProvider provider() {
        return ModelProvider.ANTHROPIC;
    }

    @Override
    protected double costCapUsd() {
        return 1.0;
    }
}
