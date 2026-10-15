package dev.langchain4j.prreviewer.github;

/** GitHub could not be read: wrong URL, missing permissions, rate limit or server failure. */
public class GitHubAccessException extends RuntimeException {

    public GitHubAccessException(String message) {
        super(message);
    }

    public GitHubAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
