package dev.langchain4j.prreviewer;

import dev.langchain4j.prreviewer.model.ModelProvider;
import dev.langchain4j.prreviewer.support.TestProfiles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.condition.EnabledIf;

/** The review on the OpenAI tiers (small, medium and large model); a few cents per run. */
@QuarkusTest
@TestProfile(TestProfiles.RealServices.class)
@EnabledIf("dev.langchain4j.prreviewer.support.Availability#openAi")
class OpenAiReviewIT extends RealModelReviewIT {

    @Override
    protected ModelProvider provider() {
        return ModelProvider.OPENAI;
    }

    @Override
    protected double costCapUsd() {
        return 0.5;
    }
}
