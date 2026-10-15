package dev.langchain4j.prreviewer.github;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.DiffStats;
import dev.langchain4j.prreviewer.model.PrSummary;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Non-AI agent: the input of the reviewer router. A decision model accepts only JSON-like values, so the triage and the
 * statistics are flattened into strings and booleans; the diff itself never reaches the router. The reviewer
 * descriptions refer to these flags by name: small decision models route far more reliably on explicit conditions
 * than on topics (measured on tev1:0.8b and jev, see the README).
 */
public class RoutingInputs {

    @Agent(name = "routingInputs", description = "Builds the compact, JSON-like input of the reviewer router",
            outputKey = Keys.ROUTING_INPUT)
    public static Map<String, Object> build(PrSummary prSummary, DiffStats diffStats) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("intent", prSummary.intent());
        input.put("category", prSummary.category().name());
        input.put("size", prSummary.size().name());
        input.put("risk", prSummary.risk().name());
        input.put("productionCodeChanged", diffStats.productionCodeChanged());
        input.put("testsChanged", diffStats.hasTests());
        input.put("touchesSecuritySensitiveCode", diffStats.touchesSecuritySensitive());
        input.put("touchesPublicApi", diffStats.touchesPublicApi());
        input.put("documentationOnly", diffStats.documentationOnly());
        input.put("touchesBuildFiles", diffStats.touchesBuild());
        return input;
    }
}
