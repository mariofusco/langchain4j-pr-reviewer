package dev.langchain4j.prreviewer.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

class RawFindingTest {

    // like the structured-output parser: null for a primitive is refused
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final TypeReference<List<RawFinding>> FINDINGS = new TypeReference<>() {};

    @Test
    void aNullConfidenceGetsTheUnknownConfidence() throws Exception {
        // a local Qwen3-4B answer: the whole reviewer used to fall back on it
        List<RawFinding> findings = JSON.readValue("""
                [{"category": "CORRECTNESS", "severity": "LOW", "file": "A.java", "line": 12,
                  "title": "Off by one", "explanation": "x", "suggestion": "y", "evidence": "z", "confidence": null}]
                """, FINDINGS);

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.confidence()).isEqualTo(RawFinding.UNKNOWN_CONFIDENCE);
            assertThat(f.line()).isEqualTo(12);
            assertThat(f.title()).isEqualTo("Off by one");
        });
    }

    @Test
    void missingLineConfidenceAndTitleGetDefaults() throws Exception {
        // a local Qwen3-4B answer, after the repair made the single object a list of one
        List<RawFinding> findings = JSON.readValue("""
                [{"category": "TESTS", "file": "AgentArgumentConversionTest.java", "severity": "MEDIUM",
                  "evidence": "The test covers conversion of Integer and Double. However, it does not cover null values.",
                  "suggestion": "Add tests for null values."}]
                """, FINDINGS);

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.line()).isZero();
            assertThat(f.confidence()).isEqualTo(RawFinding.UNKNOWN_CONFIDENCE);
            assertThat(f.title()).isEqualTo("Add tests for null values");
        });
    }

    @Test
    void aDerivedTitleIsTheFirstSentenceShortened() {
        RawFinding finding = new RawFinding(FindingCategory.CORRECTNESS, Severity.LOW, "A.java", null, " ",
                "x".repeat(300) + ". Second sentence.", null, null, null);

        assertThat(finding.title()).hasSize(RawFinding.MAX_DERIVED_TITLE).endsWith("…");
        assertThat(new RawFinding(null, null, null, null, null, null, null, null, null).title()).isEmpty();
    }
}
