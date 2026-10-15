package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.prreviewer.model.FileChange;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.RawFinding;
import dev.langchain4j.prreviewer.model.Severity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates, deduplicates, orders and identifies the findings of all reviewers, and selects the doubtful ones for a
 * debate. A finding whose file is not in the diff, or whose line is not near a changed hunk, is discarded: this
 * removes hallucinated locations, and also "findings" smuggled in through the pull request text. Two findings are
 * duplicates when different reviewers report the same category near the same line, or when the same reviewer
 * repeats itself exactly: two distinct problems one line apart reported by one reviewer are both kept.
 */
public final class Consolidation {

    public static final int LINE_TOLERANCE = 3;
    public static final int DUPLICATE_DISTANCE = 2;
    public static final double CONTEST_BELOW_CONFIDENCE = 0.8;

    public record Result(List<Finding> findings, List<Finding> contested, int discarded) {}

    private record Candidate(String reviewer, RawFinding raw, String path) {}

    private Consolidation() {}

    /** @param byReviewer the raw findings of each reviewer, in a stable order */
    public static Result consolidate(Map<String, List<RawFinding>> byReviewer, List<FileChange> changes, int maxDebates) {
        List<Candidate> candidates = new ArrayList<>();
        int discarded = 0;
        for (Map.Entry<String, List<RawFinding>> entry : byReviewer.entrySet()) {
            for (RawFinding raw : entry.getValue()) {
                if (raw == null || raw.file() == null || raw.category() == null || raw.severity() == null) {
                    discarded++;
                    continue;
                }
                FileChange change = resolve(raw.file(), changes);
                if (change == null || !change.covers(raw.line(), LINE_TOLERANCE)) {
                    discarded++;
                    continue;
                }
                candidates.add(new Candidate(entry.getKey(), raw, change.path()));
            }
        }

        List<Finding> merged = deduplicate(candidates);
        List<Finding> identified = new ArrayList<>();
        for (int i = 0; i < merged.size(); i++) {
            Finding f = merged.get(i);
            identified.add(new Finding("F" + (i + 1), f.reviewer(), f.category(), f.severity(), f.file(), f.line(),
                    f.title(), f.explanation(), f.suggestion(), f.evidence(), f.confidence(), f.verification()));
        }
        return new Result(List.copyOf(identified), contested(identified, maxDebates), discarded);
    }

    /** The file of the diff a finding refers to: exact path, or a unique suffix match. */
    static FileChange resolve(String file, List<FileChange> changes) {
        String normalized = file.strip().replaceFirst("^[ab]/", "").replaceFirst("^/", "");
        for (FileChange change : changes) {
            if (change.path().equals(normalized)) {
                return change;
            }
        }
        List<FileChange> suffixMatches = changes.stream()
                .filter(c -> c.path().endsWith("/" + normalized) || normalized.endsWith("/" + c.path()))
                .toList();
        return suffixMatches.size() == 1 ? suffixMatches.get(0) : null;
    }

    private static final Comparator<Finding> ORDER = Comparator.comparingInt((Finding f) -> f.severity().ordinal())
            .thenComparing(Comparator.comparingDouble(Finding::confidence).reversed())
            .thenComparing(Finding::file)
            .thenComparingInt(Finding::line);

    private static List<Finding> deduplicate(List<Candidate> candidates) {
        List<Finding> kept = new ArrayList<>();
        Map<Finding, Set<String>> reporters = new LinkedHashMap<>();
        List<Finding> all = candidates.stream()
                .map(c -> {
                    Finding f = Finding.from("", c.reviewer(), c.raw());
                    return new Finding(f.id(), f.reviewer(), f.category(), f.severity(), c.path(), f.line(), f.title(),
                            f.explanation(), f.suggestion(), f.evidence(), f.confidence(), f.verification());
                })
                .sorted(ORDER)
                .toList();
        for (Finding f : all) {
            Finding duplicateOf = kept.stream()
                    .filter(k -> k.file().equals(f.file()) && k.category() == f.category()
                            && sameLine(k.line(), f.line())
                            && (!k.reviewer().equals(f.reviewer())
                                    || (k.line() == f.line() && k.title().equalsIgnoreCase(f.title()))))
                    .findFirst()
                    .orElse(null);
            if (duplicateOf == null) {
                kept.add(f);
                reporters.put(f, new LinkedHashSet<>(List.of(f.reviewer())));
            } else {
                reporters.get(duplicateOf).add(f.reviewer());
            }
        }
        return kept.stream()
                .map(f -> {
                    String reviewers = String.join("+", reporters.get(f));
                    return new Finding(f.id(), reviewers, f.category(), f.severity(), f.file(), f.line(), f.title(),
                            f.explanation(), f.suggestion(), f.evidence(), f.confidence(), f.verification());
                })
                .sorted(ORDER)
                .toList();
    }

    private static boolean sameLine(int a, int b) {
        if (a == 0 || b == 0) {
            return a == b;
        }
        return Math.abs(a - b) <= DUPLICATE_DISTANCE;
    }

    /**
     * Findings worth a debate: high-severity findings without enough confidence, and high-severity findings
     * contradicted by another reviewer rating the same location as minor.
     */
    static List<Finding> contested(List<Finding> findings, int maxDebates) {
        Set<Finding> selected = new LinkedHashSet<>();
        for (Finding f : findings) {
            if (!f.severity().atLeast(Severity.HIGH)) {
                continue;
            }
            boolean doubtful = f.confidence() < CONTEST_BELOW_CONFIDENCE;
            boolean contradicted = findings.stream().anyMatch(other -> other != f
                    && !other.reviewer().equals(f.reviewer())
                    && other.file().equals(f.file())
                    && f.line() > 0 && sameLine(other.line(), f.line())
                    && !other.severity().atLeast(Severity.MEDIUM));
            if (doubtful || contradicted) {
                selected.add(f);
            }
        }
        return selected.stream()
                .sorted(Comparator.comparingInt((Finding f) -> f.severity().ordinal())
                        .thenComparingDouble(Finding::confidence))
                .limit(Math.max(maxDebates, 0))
                .toList();
    }
}
