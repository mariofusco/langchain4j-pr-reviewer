package dev.langchain4j.prreviewer.github;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
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
            typedOutputKey = Keys.RoutingInput.class)
    public static Map<String, Object> build(@K(Keys.PrSummary.class) PrSummary summary,
                                            @K(Keys.DiffStats.class) DiffStats stats) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("intent", summary.intent());
        input.put("category", summary.category().name());
        input.put("size", summary.size().name());
        input.put("risk", summary.risk().name());
        input.put("productionCodeChanged", stats.productionCodeChanged());
        input.put("testsChanged", stats.hasTests());
        input.put("touchesSecuritySensitiveCode", stats.touchesSecuritySensitive());
        input.put("touchesPublicApi", stats.touchesPublicApi());
        input.put("documentationOnly", stats.documentationOnly());
        input.put("touchesBuildFiles", stats.touchesBuild());
        return input;
    }
}
