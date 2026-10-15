package dev.langchain4j.prreviewer.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Provides {@code github.token}: {@code GITHUB_TOKEN}, else the token of the GitHub CLI ({@code gh auth token}),
 * looked up once, when the property is first read. Empty when neither is available; the GitHub client then reports
 * how to set one.
 */
public class ReviewerConfigSource implements ConfigSource {

    static final String GITHUB_TOKEN = "github.token";

    private volatile String token;

    @Override
    public Set<String> getPropertyNames() {
        return Set.of();
    }

    @Override
    public String getValue(String propertyName) {
        if (!GITHUB_TOKEN.equals(propertyName)) {
            return null;
        }
        String value = token;
        if (value == null) {
            value = resolveToken();
            token = value;
        }
        return value.isEmpty() ? null : value;
    }

    @Override
    public String getName() {
        return "reviewer-github-token";
    }

    @Override
    public int getOrdinal() {
        // below environment variables (300) and system properties (400), above application.properties (250)
        return 275;
    }

    private static String resolveToken() {
        String fromEnvironment = System.getenv("GITHUB_TOKEN");
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment.strip();
        }
        try {
            Process process = new ProcessBuilder("gh", "auth", "token").redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) {
                return output;
            }
        } catch (IOException e) {
            // gh not installed
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "";
    }
}
