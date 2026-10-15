package dev.langchain4j.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.prreviewer.model.PrRef;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PrUrlTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "https://github.com/langchain4j/langchain4j/pull/6610",
        "https://github.com/langchain4j/langchain4j/pull/6610/files",
        "http://www.github.com/langchain4j/langchain4j/pull/6610?diff=split",
        "github.com/langchain4j/langchain4j/pull/6610#discussion_r1",
        "  langchain4j/langchain4j#6610  "
    })
    void parses_urls_and_short_references(String text) {
        assertThat(PrUrl.parse(text)).isEqualTo(new PrRef("langchain4j", "langchain4j", 6610));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "https://github.com/langchain4j/langchain4j/issues/1", "https://gitlab.com/a/b/pull/1",
        "a/b#x", "https://github.com/a/pull/1"})
    void rejects_anything_else(String text) {
        assertThatThrownBy(() -> PrUrl.parse(text)).isInstanceOf(IllegalArgumentException.class);
    }
}
