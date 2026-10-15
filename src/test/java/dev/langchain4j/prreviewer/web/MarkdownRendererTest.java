package dev.langchain4j.prreviewer.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownRendererTest {

    @Test
    void raw_html_is_escaped_and_unsafe_links_are_dropped() {
        String html = MarkdownRenderer.render("Hi <script>alert(1)</script> [x](javascript:alert(1))");

        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;").doesNotContain("javascript:");
    }

    @Test
    void the_models_section_becomes_a_heading() {
        String html = MarkdownRenderer.render("<details><summary>Models used</summary>\n\n- scorer: x\n\n</details>\n");

        assertThat(html).contains("<h4>Models used</h4>").contains("<li>scorer: x</li>").doesNotContain("details");
    }

    @Test
    void tables_are_rendered() {
        assertThat(MarkdownRenderer.render("| Metric | Score |\n|---|---|\n| Security | 9.0 |\n")).contains("<table>");
    }
}
