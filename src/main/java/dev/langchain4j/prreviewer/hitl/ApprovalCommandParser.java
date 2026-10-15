package dev.langchain4j.prreviewer.hitl;

import dev.langchain4j.prreviewer.model.ApprovalDecision.Decision;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses the reviewer's answer to the report:
 * {@code approve}, {@code edit <notes>}, {@code contest F3 F7 [notes]}, {@code drop F2 [notes]} (contest and drop can
 * be combined, as in {@code contest F3 drop F2 the null check is required}), {@code reject [notes]}.
 */
public final class ApprovalCommandParser {

    private static final Pattern FINDING_ID = Pattern.compile("(?i)F\\d+[,;]?");

    private ApprovalCommandParser() {}

    /** The decision, or empty when the answer is not understood. */
    public static Optional<ApprovalDecision> parse(String answer, Set<String> knownFindingIds) {
        if (answer == null || answer.isBlank()) {
            return Optional.empty();
        }
        String[] words = answer.strip().split("\\s+");
        String command = words[0].toLowerCase(Locale.ROOT);
        String rest = answer.strip().substring(words[0].length()).strip();
        return switch (command) {
            case "approve", "approved", "a", "ok", "yes", "y" -> Optional.of(ApprovalDecision.approved());
            case "edit", "e" -> rest.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new ApprovalDecision(Decision.EDIT, rest, List.of(), List.of()));
            case "reject", "r" -> Optional.of(new ApprovalDecision(Decision.REJECTED, rest, List.of(), List.of()));
            case "contest", "drop", "c", "d" -> contestOrDrop(words, knownFindingIds);
            default -> Optional.empty();
        };
    }

    private static Optional<ApprovalDecision> contestOrDrop(String[] words, Set<String> knownFindingIds) {
        List<String> contested = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> target = null;
        for (String word : words) {
            String lower = word.toLowerCase(Locale.ROOT);
            if (notes.isEmpty() && (lower.equals("contest") || lower.equals("c"))) {
                target = contested;
            } else if (notes.isEmpty() && (lower.equals("drop") || lower.equals("d"))) {
                target = dropped;
            } else if (notes.isEmpty() && target != null && FINDING_ID.matcher(word).matches()) {
                String id = word.replaceAll("[,;]", "").toUpperCase(Locale.ROOT);
                if (!knownFindingIds.contains(id)) {
                    return Optional.empty();
                }
                target.add(id);
            } else {
                notes.add(word);
            }
        }
        if (contested.isEmpty() && dropped.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ApprovalDecision(Decision.CONTEST, String.join(" ", notes), List.copyOf(contested),
                List.copyOf(dropped)));
    }
}
