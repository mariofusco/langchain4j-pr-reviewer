package dev.langchain4j.prreviewer.keys;

import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.ArrayList;
import java.util.List;

/** Appends to list-valued keys. Synchronized because parallel reviewers may fail and append at the same time. */
public final class ScopeLists {

    private static final Object LOCK = new Object();

    private ScopeLists() {}

    public static void append(AgenticScope scope, Class<? extends TypedKey<List<String>>> key, String value) {
        synchronized (LOCK) {
            List<String> values = new ArrayList<>(scope.readState(key));
            if (!values.contains(value)) {
                values.add(value);
                scope.writeState(key, List.copyOf(values));
            }
        }
    }
}
