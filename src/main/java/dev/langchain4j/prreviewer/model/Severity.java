package dev.langchain4j.prreviewer.model;

/** Finding severities, declared from the most to the least severe. */
public enum Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    NIT;

    public boolean atLeast(Severity other) {
        return ordinal() <= other.ordinal();
    }
}
