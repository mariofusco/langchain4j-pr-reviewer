package dev.langchain4j.prreviewer.models;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import org.junit.jupiter.api.Test;

class EffortHttpClientBuilderTest {

    private static HttpRequest request(String url, String body) {
        return HttpRequest.builder().method(HttpMethod.POST).url(url).addHeader("x-api-key", "k").body(body).build();
    }

    @Test
    void merges_effort_into_the_existing_output_config() {
        HttpRequest result = EffortHttpClientBuilder.withEffort(request("https://api.anthropic.com/v1/messages",
                "{\"model\":\"m\",\"messages\":[],\"output_config\":{\"format\":{\"type\":\"json_schema\"}}}"), "low");

        assertThat(result.body()).contains("\"output_config\":{\"format\":{\"type\":\"json_schema\"},\"effort\":\"low\"}");
        assertThat(result.headers()).containsKey("x-api-key");
    }

    @Test
    void creates_the_output_config_when_absent() {
        HttpRequest result = EffortHttpClientBuilder.withEffort(
                request("https://api.anthropic.com/v1/messages", "{\"model\":\"m\",\"messages\":[]}"), "medium");

        assertThat(result.body()).contains("\"output_config\":{\"effort\":\"medium\"}");
    }

    @Test
    void keeps_an_explicit_effort_and_ignores_other_endpoints() {
        String explicit = "{\"model\":\"m\",\"messages\":[],\"output_config\":{\"effort\":\"max\"}}";
        assertThat(EffortHttpClientBuilder.withEffort(request("https://x/v1/messages", explicit), "low").body())
                .isEqualTo(explicit);

        String body = "{\"model\":\"m\",\"messages\":[]}";
        assertThat(EffortHttpClientBuilder.withEffort(request("https://x/v1/messages/count_tokens", body), "low").body())
                .isEqualTo(body);
        assertThat(EffortHttpClientBuilder.withEffort(request("https://x/v1/models", body), "low").body())
                .isEqualTo(body);
    }
}
