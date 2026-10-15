package dev.langchain4j.prreviewer.model;

import java.util.List;

/**
 * One hunk of a unified diff.
 *
 * @param lines the hunk body, each line keeping its leading '+', '-' or ' '
 */
public record Hunk(int oldStart, int oldCount, int newStart, int newCount, String header, List<String> lines) {

    /** Whether the given new-file line falls inside this hunk, widened by the given tolerance. */
    public boolean coversNewLine(int line, int tolerance) {
        return line >= newStart - tolerance && line < newStart + Math.max(newCount, 1) + tolerance;
    }

    /** Whether the given old-file line falls inside this hunk, widened by the given tolerance. */
    public boolean coversOldLine(int line, int tolerance) {
        return line >= oldStart - tolerance && line < oldStart + Math.max(oldCount, 1) + tolerance;
    }
}
