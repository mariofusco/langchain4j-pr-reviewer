package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.prreviewer.models.DecisionModels;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** The router through the OpenAI Decisions API, when the account has access to it. */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiRouterIT extends ReviewRouterIT {

    @Override
    protected DecisionModels.Provider provider() {
        return DecisionModels.Provider.OPENAI;
    }
}
