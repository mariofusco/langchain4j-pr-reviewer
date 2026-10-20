package dev.langchain4j.prreviewer.github;

import java.util.Locale;

/** GitHub could not be read: wrong URL, missing permissions, rate limit or server failure. */
public class GitHubAccessException extends RuntimeException {

    private final int status;

    public GitHubAccessException(String message) {
        this(message, 0, null);
    }

    public GitHubAccessException(String message, Throwable cause) {
        this(message, 0, cause);
    }

    /** @param status the HTTP status of the refusal, or 0 when there is none */
    public GitHubAccessException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }

    /** Refused for the primary or the secondary rate limit, which hub4j reports instead of waiting: worth a retry. */
    public boolean rateLimited() {
        return status == 429 || (status == 403 && getMessage().toLowerCase(Locale.ROOT).contains("limit"));
    }
}
