package dev.langchain4j.prreviewer.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocalOutputRepairTest {

    @Test
    void aListWrappedUnderTheItemsNameIsUnwrapped() {
        assertThat(LocalOutputRepair.repair("{\"findings\": [{\"file\": \"A.java\"}]}", true))
                .isEqualTo("[{\"file\":\"A.java\"}]");
    }

    @Test
    void anEmptyObjectForAListIsAnEmptyList() {
        assertThat(LocalOutputRepair.repair("{}", true)).isEqualTo("[]");
        assertThat(LocalOutputRepair.repair(" { } ", true)).isEqualTo("[]");
        assertThat(LocalOutputRepair.repair("{}", false)).isEqualTo("{}");
    }

    @Test
    void theJsonInProseIsExtracted() {
        assertThat(LocalOutputRepair.repair("I checked the diff. Findings:\n[{\"line\": 3}]\nThat is all.", true))
                .isEqualTo("[{\"line\": 3}]");
        assertThat(LocalOutputRepair.repair("Here is my answer: {\"findings\": [{\"line\": 3}]}", true))
                .isEqualTo("[{\"line\": 3}]");
        assertThat(LocalOutputRepair.repair("The score: {\"value\": 7}.", false)).isEqualTo("{\"value\": 7}");
        // a record answer never becomes the array inside it
        assertThat(LocalOutputRepair.repair("Issues [a] and {\"value\": 7}", false)).isEqualTo("{\"value\": 7}");
        assertThat(LocalOutputRepair.repair("No JSON here [at all]", true)).isEqualTo("No JSON here [at all]");
    }

    @Test
    void anAnswerInACodeFenceIsUnwrapped() {
        assertThat(LocalOutputRepair.repair("```json\n{\"value\": 3}\n```", false)).isEqualTo("{\"value\": 3}");
        assertThat(LocalOutputRepair.repair("```\n{\"findings\": []}\n```", true)).isEqualTo("[]");
    }

    @Test
    void aSingleItemForAListBecomesAListOfOne() {
        assertThat(LocalOutputRepair.repair("{\"file\": \"A.java\", \"line\": 3, \"confidence\": null}", true))
                .isEqualTo("[{\"file\":\"A.java\",\"line\":3,\"confidence\":null}]");
        assertThat(LocalOutputRepair.repair("```json\n{\"file\": \"A.java\"}\n```", true)).isEqualTo("[{\"file\":\"A.java\"}]");
        // a record answer stays a record
        assertThat(LocalOutputRepair.repair("{\"file\": \"A.java\"}", false)).isEqualTo("{\"file\": \"A.java\"}");
        // an object holding an object is a wrong shape, not an item
        assertThat(LocalOutputRepair.repair("{\"finding\": {\"file\": \"A.java\"}}", true))
                .isEqualTo("{\"finding\": {\"file\": \"A.java\"}}");
    }

    @Test
    void whatIsAlreadyRightOrNotRepairableIsLeftAlone() {
        assertThat(LocalOutputRepair.repair("{\"values\": []}", true)).isEqualTo("{\"values\": []}");
        assertThat(LocalOutputRepair.repair("[{\"file\": \"A.java\"}]", true)).isEqualTo("[{\"file\": \"A.java\"}]");
        // two properties: not a list answer under another name
        assertThat(LocalOutputRepair.repair("{\"a\": [], \"b\": []}", true)).isEqualTo("{\"a\": [], \"b\": []}");
        // a record answer that happens to hold one list
        assertThat(LocalOutputRepair.repair("{\"questions\": [\"why?\"]}", false)).isEqualTo("{\"questions\": [\"why?\"]}");
        assertThat(LocalOutputRepair.repair("{not json", true)).isEqualTo("{not json");
        assertThat(LocalOutputRepair.repair(null, true)).isNull();
    }
}
