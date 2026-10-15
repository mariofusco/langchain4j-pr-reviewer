package dev.langchain4j.prreviewer.model;

/** The judge's decision about a contested finding. */
public record DebateVerdict(DebateOutcome outcome, Severity adjustedSeverity, String reasoning) {}
