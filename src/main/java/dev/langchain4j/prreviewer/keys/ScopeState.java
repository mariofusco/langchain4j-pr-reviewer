package dev.langchain4j.prreviewer.keys;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.ArrayList;
import java.util.List;

/** Typed access to the String keys of {@link Keys}, with their defaults. */
public final class ScopeState {

    private static final Object LOCK = new Object();

    private ScopeState() {}

    /** The value of the key, or its default from {@link Keys#DEFAULTS} when it is unset or null. */
    @SuppressWarnings("unchecked")
    public static <T> T read(AgenticScope scope, String key) {
        Object value = scope.readState(key);
        return (T) (value != null ? value : Keys.DEFAULTS.get(key));
    }

    /** Resets a key to its default from {@link Keys#DEFAULTS}, or clears it when it has none. */
    public static void reset(AgenticScope scope, String key) {
        scope.writeState(key, Keys.DEFAULTS.get(key));
    }

    /** Writes the default of every key that has one and is not set yet. */
    public static void seedDefaults(AgenticScope scope) {
        Keys.DEFAULTS.forEach(scope::writeStateIfAbsent);
    }

    /** Appends to a list-valued key. Synchronized because parallel reviewers may fail and append at the same time. */
    public static void append(AgenticScope scope, String key, String value) {
        synchronized (LOCK) {
            List<String> values = new ArrayList<>(ScopeState.<List<String>>read(scope, key));
            if (!values.contains(value)) {
                values.add(value);
                scope.writeState(key, List.copyOf(values));
            }
        }
    }
}
