package dev.langchain4j.prreviewer.github;

import dev.langchain4j.prreviewer.model.ChangeKind;
import dev.langchain4j.prreviewer.model.FileChange;
import dev.langchain4j.prreviewer.model.Hunk;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses a git unified diff into per-file changes. Classification is left to {@link DiffPreprocessing}. */
public final class DiffParser {

    private static final Pattern DIFF_HEADER = Pattern.compile("^diff --git (\"?)a/(.+?)\\1 (\"?)b/(.+?)\\3$");
    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@ ?(.*)$");

    private DiffParser() {}

    public static List<FileChange> parse(String diff) {
        List<FileChange> files = new ArrayList<>();
        if (diff == null || diff.isBlank()) {
            return files;
        }
        FileBuilder current = null;
        HunkBuilder hunk = null;
        for (String line : diff.split("\n", -1)) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.startsWith("diff --git ")) {
                if (current != null) {
                    files.add(current.build(hunk));
                }
                current = new FileBuilder(line);
                hunk = null;
                continue;
            }
            if (current == null) {
                continue;
            }
            if (hunk == null || !hunk.accepts(line)) {
                Matcher hunkHeader = HUNK_HEADER.matcher(line);
                if (hunkHeader.matches()) {
                    current.addHunk(hunk);
                    hunk = new HunkBuilder(hunkHeader);
                    continue;
                }
                if (hunk == null) {
                    current.header(line);
                    continue;
                }
            }
            if (hunk.accepts(line)) {
                hunk.add(line);
                if (line.startsWith("+")) {
                    current.additions++;
                } else if (line.startsWith("-")) {
                    current.deletions++;
                }
            }
        }
        if (current != null) {
            files.add(current.build(hunk));
        }
        return files;
    }

    private static final class FileBuilder {
        private String oldPath;
        private String newPath;
        private ChangeKind kind = ChangeKind.MODIFIED;
        private boolean binary;
        private int additions;
        private int deletions;
        private final List<Hunk> hunks = new ArrayList<>();

        FileBuilder(String diffLine) {
            Matcher matcher = DIFF_HEADER.matcher(diffLine);
            if (matcher.matches()) {
                oldPath = matcher.group(2);
                newPath = matcher.group(4);
            } else {
                String rest = diffLine.substring("diff --git ".length());
                int split = rest.indexOf(" b/");
                oldPath = split > 0 ? rest.substring(2, split) : rest;
                newPath = split > 0 ? rest.substring(split + 3) : rest;
            }
        }

        void header(String line) {
            if (line.startsWith("new file mode")) {
                kind = ChangeKind.ADDED;
            } else if (line.startsWith("deleted file mode")) {
                kind = ChangeKind.DELETED;
            } else if (line.startsWith("rename from ")) {
                kind = ChangeKind.RENAMED;
                oldPath = line.substring("rename from ".length());
            } else if (line.startsWith("rename to ")) {
                kind = ChangeKind.RENAMED;
                newPath = line.substring("rename to ".length());
            } else if (line.startsWith("Binary files ") || line.startsWith("GIT binary patch")) {
                binary = true;
            } else if (line.startsWith("--- ") && line.endsWith("/dev/null")) {
                kind = ChangeKind.ADDED;
            } else if (line.startsWith("+++ ") && line.endsWith("/dev/null")) {
                kind = ChangeKind.DELETED;
            }
        }

        void addHunk(HunkBuilder hunk) {
            if (hunk != null) {
                hunks.add(hunk.build());
            }
        }

        FileChange build(HunkBuilder last) {
            addHunk(last);
            String path = kind == ChangeKind.DELETED ? oldPath : newPath;
            return new FileChange(path, oldPath, kind, null, false, false, binary, additions, deletions,
                    List.copyOf(hunks));
        }
    }

    private static final class HunkBuilder {
        private final int oldStart;
        private final int oldCount;
        private final int newStart;
        private final int newCount;
        private final String header;
        private final List<String> lines = new ArrayList<>();
        private int oldSeen;
        private int newSeen;

        HunkBuilder(Matcher header) {
            this.oldStart = Integer.parseInt(header.group(1));
            this.oldCount = header.group(2) == null ? 1 : Integer.parseInt(header.group(2));
            this.newStart = Integer.parseInt(header.group(3));
            this.newCount = header.group(4) == null ? 1 : Integer.parseInt(header.group(4));
            this.header = header.group(5);
        }

        /** A hunk accepts lines until both sides have their declared number of lines. */
        boolean accepts(String line) {
            if (line.startsWith("\\")) {
                return true;
            }
            boolean full = oldSeen >= oldCount && newSeen >= newCount;
            return !full && (line.startsWith("+") || line.startsWith("-") || line.startsWith(" ") || line.isEmpty());
        }

        void add(String line) {
            if (line.startsWith("\\")) {
                return;
            }
            String normalized = line.isEmpty() ? " " : line;
            switch (normalized.charAt(0)) {
                case '+' -> newSeen++;
                case '-' -> oldSeen++;
                default -> {
                    oldSeen++;
                    newSeen++;
                }
            }
            lines.add(normalized);
        }

        Hunk build() {
            return new Hunk(oldStart, oldCount, newStart, newCount, header, List.copyOf(lines));
        }
    }
}
