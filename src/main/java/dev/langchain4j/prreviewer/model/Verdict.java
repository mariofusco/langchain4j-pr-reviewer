package dev.langchain4j.prreviewer.model;

/** Review verdicts, declared from the most lenient to the strictest. */
public enum Verdict {
    APPROVE,
    COMMENT,
    REQUEST_CHANGES;

    public static Verdict stricter(Verdict a, Verdict b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    public boolean stricterThan(Verdict other) {
        return ordinal() > other.ordinal();
    }
}
