package dev.langchain4j.prreviewer.models;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Small repairs of a local model's structured answers. A hosted model is held to the JSON schema by the API; a local
 * one only reads the schema in its prompt (jitllm has no constrained decoding), and gets the shape right more often
 * than the names. These slips are common and safe to undo:
 *
 * <ul>
 *   <li>the JSON wrapped in a Markdown code fence, or in prose;
 *   <li>a list answer wrapped in an object under the name the prompt uses for the items, for example
 *       {@code {"findings": [...]}}. LangChain4j shows no JSON format for a list of records when the model has no
 *       JSON-schema support (it does for a record), so the model invents the wrapper; the list parser takes a bare
 *       array, or {@code {"values": [...]}};
 *   <li>an empty list returned as an empty object, {@code {}};
 *   <li>a list of one returned as that one item, a bare object whose values are all scalars, for example a single
 *       finding: it becomes a list of that item. An object holding an array or an object is left alone, since it is
 *       more likely a wrong shape than an item.
 * </ul>
 *
 * Anything else is returned unchanged, so a wrong answer still fails as it would have.
 */
final class LocalOutputRepair {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern FENCE = Pattern.compile("^\\s*```(?:json)?\\s*\\n(.*?)\\n\\s*```\\s*$", Pattern.DOTALL);

    private LocalOutputRepair() {}

    /**
     * @param text          the model's answer
     * @param listExpected  whether the answer is meant to be a list: the request shows no JSON format, which LangChain4j
     *                      does for every record answer
     */
    static String repair(String text, boolean listExpected) {
        if (text == null) {
            return null;
        }
        String repaired = text;
        Matcher fence = FENCE.matcher(repaired);
        if (fence.matches()) {
            repaired = fence.group(1).trim();
        }
        repaired = extractJson(repaired, listExpected);
        if (listExpected && repaired.trim().startsWith("{")) {
            try {
                JsonNode node = JSON.readTree(repaired);
                if (node instanceof ObjectNode object && object.isEmpty()) {
                    repaired = "[]";
                } else if (node instanceof ObjectNode object && object.size() == 1 && !object.has("values")) {
                    Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
                    Map.Entry<String, JsonNode> only = fields.next();
                    if (only.getValue().isArray()) {
                        repaired = JSON.writeValueAsString(only.getValue());
                    } else if (onlyScalars(object)) {
                        repaired = JSON.writeValueAsString(JSON.createArrayNode().add(object));
                    }
                } else if (node instanceof ObjectNode object && onlyScalars(object)) {
                    repaired = JSON.writeValueAsString(JSON.createArrayNode().add(object));
                }
            } catch (Exception e) {
                // not JSON after all: left for the output parser to report
            }
        }
        return repaired;
    }

    private static boolean onlyScalars(ObjectNode object) {
        for (JsonNode value : object) {
            if (value.isContainerNode()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The JSON value in an answer that wraps it in prose ("I reviewed the diff... [ ... ]"), as a model answering
     * without thinking tends to: the span from the first opening bracket to the last closing one, when it parses. A
     * list answer looks for an array first, a record answer for an object only. Otherwise the text is unchanged.
     */
    private static String extractJson(String text, boolean listExpected) {
        String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return text;
        }
        for (char open : listExpected ? new char[] {'[', '{'} : new char[] {'{'}) {
            char close = open == '[' ? ']' : '}';
            int start = trimmed.indexOf(open);
            int end = trimmed.lastIndexOf(close);
            if (start >= 0 && end > start) {
                String candidate = trimmed.substring(start, end + 1);
                try {
                    JSON.readTree(candidate);
                    return candidate;
                } catch (Exception e) {
                    // not a JSON value: try the next bracket
                }
            }
        }
        return text;
    }
}
