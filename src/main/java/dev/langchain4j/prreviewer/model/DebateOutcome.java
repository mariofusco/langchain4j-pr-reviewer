package dev.langchain4j.prreviewer.model;

/** The outcomes a debate judge may reach about a contested finding. */
public enum DebateOutcome {
    CONFIRMED,
    REJECTED,
    UNSURE;

    public Verification toVerification() {
        return Verification.valueOf(name());
    }
}
