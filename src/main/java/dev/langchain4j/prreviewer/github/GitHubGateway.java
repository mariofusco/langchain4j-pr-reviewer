package dev.langchain4j.prreviewer.github;

import java.util.Map;

/** Calls a read-only GitHub MCP tool and returns its text result. */
@FunctionalInterface
public interface GitHubGateway {

    String call(String tool, Map<String, Object> arguments);
}
