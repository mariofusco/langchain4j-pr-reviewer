package dev.langchain4j.prreviewer.model;

import java.util.List;

/** Scores and verdict as proposed by a scorer model, before calibration. */
public record ScoreCard(
        List<MetricScore> metrics, Verdict proposedVerdict, String verdictReasoning, double confidence) {}
