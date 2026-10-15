package dev.langchain4j.prreviewer.web;

import java.util.List;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Renders reports to HTML. Pull request text flows into the report, so raw HTML is escaped and link URLs are
 * sanitized: the report can never inject markup or scripts into the page.
 */
final class MarkdownRenderer {

    private static final List<org.commonmark.Extension> EXTENSIONS = List.of(TablesExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .build();

    private MarkdownRenderer() {}

    static String render(String markdown) {
        return RENDERER.render(PARSER.parse(collapsibleSectionsAsHeadings(markdown == null ? "" : markdown)));
    }

    /**
     * report.md folds the models used into a {@code <details>} block, which GitHub renders; with raw HTML escaped it
     * would show as text, so the block's own lines become a heading. Only lines that are exactly such tags change.
     */
    static String collapsibleSectionsAsHeadings(String markdown) {
        return markdown.replaceAll("(?m)^<details><summary>([^<>]*)</summary>[ \\t]*$", "#### $1")
                .replaceAll("(?m)^</details>[ \\t]*$", "");
    }
}
