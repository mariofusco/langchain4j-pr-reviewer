package dev.langchain4j.prreviewer.model;

import java.util.List;

/** The changes of one file in a pull request. */
public record FileChange(
        String path,
        String oldPath,
        ChangeKind kind,
        String language,
        boolean test,
        boolean generated,
        boolean binary,
        int additions,
        int deletions,
        List<Hunk> hunks) {

    /** Whether a finding at the given line can refer to this change (0 means the whole file). */
    public boolean covers(int line, int tolerance) {
        if (line <= 0) {
            return true;
        }
        return hunks.stream()
                .anyMatch(hunk -> hunk.coversNewLine(line, tolerance)
                        || (kind == ChangeKind.DELETED && hunk.coversOldLine(line, tolerance)));
    }

    public FileChange withClassification(String language, boolean test, boolean generated) {
        return new FileChange(path, oldPath, kind, language, test, generated, binary, additions, deletions, hunks);
    }
}
