package dev.langchain4j.prreviewer.github;

import dev.langchain4j.prreviewer.model.PrRef;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses {@code https://github.com/owner/repo/pull/123[/...]} and {@code owner/repo#123}. */
public final class PrUrl {

    private static final Pattern URL = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?github\\.com/([\\w.-]+)/([\\w.-]+)/pull/(\\d+)(?:[/?#].*)?$");
    private static final Pattern SHORT = Pattern.compile("^([\\w.-]+)/([\\w.-]+)#(\\d+)$");

    private PrUrl() {}

    public static PrRef parse(String text) {
        String value = text == null ? "" : text.strip();
        Matcher matcher = URL.matcher(value);
        if (!matcher.matches()) {
            matcher = SHORT.matcher(value);
        }
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a GitHub pull request URL or owner/repo#number reference: '"
                    + text + "'");
        }
        return new PrRef(matcher.group(1), matcher.group(2), Integer.parseInt(matcher.group(3)));
    }
}
