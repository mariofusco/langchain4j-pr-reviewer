package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.prreviewer.model.DecisionProvider;
import dev.langchain4j.prreviewer.support.TestProfiles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * The router with the local decision model (Ollama, {@code tev1:0.8b}). Its probabilities are printed; see
 * {@link HostedReviewRouterIT} for the router alone.
 */
@QuarkusTest
@TestProfile(TestProfiles.RealDecisionModels.class)
@EnabledIf("dev.langchain4j.prreviewer.support.Availability#localDecisionModel")
class ReviewRouterIT extends AbstractReviewRouterIT {

    @Override
    protected DecisionProvider provider() {
        return DecisionProvider.LOCAL;
    }
}
