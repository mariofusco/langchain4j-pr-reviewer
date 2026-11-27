package dev.langchain4j.prreviewer.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import dev.langchain4j.prreviewer.model.ModelProvider;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.observability.LocalInference;
import org.junit.jupiter.api.Test;

class LocalProviderTest {

    @Test
    void theLocalProviderNeedsNoKeyAndNamesOneModelPerTier() {
        assertThat(ModelProvider.JITLLM.apiKeyVariable()).isNull();
        assertThat(ModelProvider.JITLLM.configName(ModelTier.ECONOMY)).isEqualTo("jitllm-economy");
        assertThat(ModelProvider.JITLLM.configName(ModelTier.PREMIUM)).isEqualTo("jitllm-premium");
    }

    @Test
    void theLocalModelIsNamedAfterItsFile() {
        assertThat(LocalInference.modelName(Path.of("/m/Qwen3.8-27B-Q4_0.gguf"))).isEqualTo("jitllm/Qwen3.8-27B-Q4_0");
    }

    @Test
    void theSpeedOfACallCountsItsWholeRunTime() {
        assertThat(new LocalInference.Call("jitllm/m", "a", 1000, 200, 0, 10_000).tokensPerSecond()).isEqualTo(20.0);
        assertThat(new LocalInference.Call("jitllm/m", "a", 1000, 200, 0, 0).tokensPerSecond()).isZero();
    }

    @Test
    void thePanelSumsTheCallsOfTheReview() {
        var calls = List.of(new LocalInference.Call("jitllm/small", "triage", 3000, 300, 0, 30_000),
                new LocalInference.Call("jitllm/large", "correctness", 5000, 900, 4_000, 60_000));
        var view = dev.langchain4j.prreviewer.web.ViewsAccess.gpu(calls);
        assertThat(view).containsEntry("calls", "2").containsEntry("outputTokens", "1200")
                .containsEntry("average", "13.3").containsEntry("peak", "15.0").containsEntry("queued", "4");
    }
}
