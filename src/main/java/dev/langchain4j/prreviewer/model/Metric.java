package dev.langchain4j.prreviewer.model;

/** The scored metrics, with their weight in the overall score. */
public enum Metric {
    USEFULNESS("Usefulness", 0.20),
    CORRECTNESS("Correctness", 0.30),
    CONCISENESS("Conciseness", 0.10),
    SECURITY("Security", 0.15),
    TEST_COVERAGE("Test coverage", 0.10),
    MAINTAINABILITY("Maintainability", 0.10),
    API_AND_DOCS("API and docs", 0.05),
    PR_HYGIENE("PR hygiene", 0.00);

    private final String label;
    private final double weight;

    Metric(String label, double weight) {
        this.label = label;
        this.weight = weight;
    }

    public String label() {
        return label;
    }

    public double weight() {
        return weight;
    }
}
