package dev.langchain4j.prreviewer.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.prreviewer.model.Category;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.Risk;
import dev.langchain4j.prreviewer.model.Size;
import dev.langchain4j.prreviewer.models.DecisionModels;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The router alone with the hosted TypeSafe decision model: it selects exactly the expected reviewers, so the coverage
 * floor has nothing to add. Sends only the compact routing input of synthetic changes to the hosted service.
 */
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
class HostedReviewRouterIT extends ReviewRouterIT {

    @Override
    protected DecisionModels.Provider provider() {
        return DecisionModels.Provider.TYPESAFE;
    }

    @Test
    void router_alone_selects_the_expected_reviewers() {
        assertThat(routerChoice(SQL_FIX, new PrSummary("Implement user lookup by name", Category.BUGFIX, Size.SMALL,
                Risk.HIGH, List.of("SQL"), false, List.of())))
                .containsExactlyInAnyOrder("correctnessReviewer", "securityReviewer", "testReviewer");
        assertThat(routerChoice(DOCS, new PrSummary("Document Gradle installation", Category.DOCS, Size.SMALL,
                Risk.LOW, List.of("README"), false, List.of())))
                .containsExactly("docsApiReviewer");
    }
}
