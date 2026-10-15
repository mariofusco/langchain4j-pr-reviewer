package dev.langchain4j.prreviewer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.langchain4j.prreviewer.model.ModelProvider;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.models.ModelCatalog;
import dev.langchain4j.prreviewer.support.TestChatModels;
import dev.langchain4j.prreviewer.support.TestDecisionModels;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The model clients Quarkus builds report account problems in a way the error handler recognizes, so a review with an
 * invalid key or an unknown model stops at the first agent. The requests are rejected before any token is spent.
 */
@QuarkusTest
@TestProfile(AccountErrorsIT.BrokenAccounts.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AccountErrorsIT {

    public static class BrokenAccounts implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.arc.exclude-types", TestChatModels.class.getName() + "," + TestDecisionModels.class.getName(),
                    "quarkus.langchain4j.openai.openai-economy.api-key", "sk-invalid",
                    "quarkus.langchain4j.openai.openai-standard.chat-model.model-name", "gpt-no-such-model",
                    "quarkus.langchain4j.anthropic.anthropic-economy.api-key", "sk-ant-invalid");
        }
    }

    @Inject
    ModelCatalog models;

    @Test
    void invalid_openai_key_is_an_account_problem() {
        Throwable error = catchThrowable(() -> models.chatModel(ModelProvider.OPENAI, ModelTier.ECONOMY).chat("Hi"));

        assertThat(error).isNotNull();
        assertThat(RootErrorHandler.accountProblem(error)).as(error.toString()).isTrue();
    }

    @Test
    void unknown_openai_model_is_an_account_problem() {
        Throwable error = catchThrowable(() -> models.chatModel(ModelProvider.OPENAI, ModelTier.STANDARD).chat("Hi"));

        assertThat(error).isNotNull();
        assertThat(RootErrorHandler.accountProblem(error)).as(error.toString()).isTrue();
    }

    @Test
    void invalid_anthropic_key_is_an_account_problem() {
        Throwable error = catchThrowable(() -> models.chatModel(ModelProvider.ANTHROPIC, ModelTier.ECONOMY).chat("Hi"));

        assertThat(error).isNotNull();
        assertThat(RootErrorHandler.accountProblem(error)).as(error.toString()).isTrue();
    }
}
