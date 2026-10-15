package dev.langchain4j.prreviewer.model;

import java.util.List;

/**
 * Deterministic facts about the diff, used for routing, the reviewer coverage floor, model escalation and score
 * calibration.
 *
 * @param productionCodeChanged whether code outside tests and generated files changed
 * @param documentationOnly     whether only documentation files changed
 * @param omittedFiles files left out of the reviewed diff because of the size limits
 * @param skippedFiles generated and binary files, which are never reviewed
 */
public record DiffStats(
        int files,
        int additions,
        int deletions,
        List<String> languages,
        boolean hasTests,
        boolean productionCodeChanged,
        boolean documentationOnly,
        boolean touchesSecuritySensitive,
        boolean touchesPublicApi,
        boolean touchesBuild,
        boolean truncated,
        int estimatedTokens,
        List<String> omittedFiles,
        List<String> skippedFiles) {}
