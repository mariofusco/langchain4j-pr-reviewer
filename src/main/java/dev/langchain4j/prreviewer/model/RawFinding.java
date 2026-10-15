package dev.langchain4j.prreviewer.model;

/**
 * A finding as returned by a reviewer model, before validation and identification.
 *
 * @param line       the line in the new version of the file, 0 when the finding concerns the whole file
 * @param evidence   the exact code from the diff that supports the finding
 * @param confidence from 0.0 to 1.0
 */
public record RawFinding(
        FindingCategory category,
        Severity severity,
        String file,
        int line,
        String title,
        String explanation,
        String suggestion,
        String evidence,
        double confidence) {}
