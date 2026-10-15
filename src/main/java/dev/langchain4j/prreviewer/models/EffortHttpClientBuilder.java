package dev.langchain4j.prreviewer.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Adds {@code output_config.effort} to every Anthropic Messages request.
 * <p>
 * {@code langchain4j-anthropic} 1.22.0 has no effort option, and its custom parameters are written at the top level of
 * the request, where an {@code output_config} would clash with the one carrying the structured output format. This
 * builder wraps the HTTP client and merges the effort into the request's own {@code output_config} instead.
 */
public final class EffortHttpClientBuilder implements HttpClientBuilder {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClientBuilder delegate;
    private final String effort;

    public EffortHttpClientBuilder(HttpClientBuilder delegate, String effort) {
        this.delegate = delegate;
        this.effort = effort;
    }

    @Override
    public Duration connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public HttpClientBuilder connectTimeout(Duration timeout) {
        delegate.connectTimeout(timeout);
        return this;
    }

    @Override
    public Duration readTimeout() {
        return delegate.readTimeout();
    }

    @Override
    public HttpClientBuilder readTimeout(Duration timeout) {
        delegate.readTimeout(timeout);
        return this;
    }

    @Override
    public HttpClient build() {
        HttpClient client = delegate.build();
        return new HttpClient() {
            @Override
            public SuccessfulHttpResponse execute(HttpRequest request) {
                return client.execute(withEffort(request, effort));
            }

            @Override
            public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
                return client.executeAsync(withEffort(request, effort));
            }

            @Override
            public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
                client.execute(withEffort(request, effort), parser, listener);
            }
        };
    }

    /** Returns the request with the effort merged into the {@code output_config} of a Messages request body. */
    static HttpRequest withEffort(HttpRequest request, String effort) {
        String body = request.body();
        if (body == null || !request.url().contains("/messages") || request.url().contains("/count_tokens")) {
            return request;
        }
        try {
            JsonNode json = JSON.readTree(body);
            if (!(json instanceof ObjectNode root) || !root.has("messages")) {
                return request;
            }
            JsonNode existing = root.get("output_config");
            ObjectNode outputConfig = existing instanceof ObjectNode node ? node : root.putObject("output_config");
            if (!outputConfig.has("effort")) {
                outputConfig.put("effort", effort);
            }
            return HttpRequest.builder()
                    .method(request.method())
                    .url(request.url())
                    .headers(request.headers())
                    .body(JSON.writeValueAsString(root))
                    .build();
        } catch (IOException e) {
            return request;
        }
    }
}
