package dev.langchain4j.prreviewer.model;

import java.util.List;
import java.util.Map;

/** How a review was produced: mode, reviewers, models, cost. */
public record RunInfo(
        ReviewMode mode,
        List<String> reviewersRun,
        Map<String, String> modelsByAgent,
        double costUsd,
        int revisionRounds) {}
