package dev.langchain4j.prreviewer.support;

import dev.langchain4j.prreviewer.github.GitHubGateway;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Answers GitHub MCP calls from responses recorded from the real server, in {@code src/test/resources/mcp}. */
public final class FixtureGitHubGateway implements GitHubGateway {

    private final String pr;
    private final String diffOverride;
    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());

    /** @param pr the fixture directory, such as "pr6610" */
    public FixtureGitHubGateway(String pr) {
        this(pr, null);
    }

    /** Uses the fixtures of the given pull request, but with another diff. */
    public FixtureGitHubGateway(String pr, String diffOverride) {
        this.pr = pr;
        this.diffOverride = diffOverride;
    }

    @Override
    public String call(String tool, Map<String, Object> arguments) {
        String method = String.valueOf(arguments.get("method"));
        calls.add(tool + ":" + method);
        if (tool.equals("issue_read")) {
            return read(pr + "/issue_" + arguments.get("issue_number") + ".json");
        }
        if (!tool.equals("pull_request_read")) {
            throw new IllegalArgumentException("Unexpected tool " + tool);
        }
        if (method.equals("get_diff")) {
            return diffOverride != null ? diffOverride : read(pr + "/get_diff.json");
        }
        return read(pr + "/" + method + ".json");
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    /** The raw unified diff of pull request 6610, as plain text. */
    public static String diff6610() {
        return read("plain/get_diff.diff");
    }

    public static String read(String path) {
        try (InputStream in = FixtureGitHubGateway.class.getResourceAsStream("/mcp/" + path)) {
            if (in == null) {
                throw new IllegalArgumentException("No fixture /mcp/" + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
