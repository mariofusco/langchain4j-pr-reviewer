package dev.langchain4j.prreviewer.observability;

import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records the usage of every model call, including each round trip of tool-using agents, in the ledger, and logs
 * one JSON line per call on the {@code dev.langchain4j.prreviewer.usage} logger.
 */
public final class CostTrackingListener implements ChatModelListener {

    private static final Logger USAGE = LoggerFactory.getLogger("dev.langchain4j.prreviewer.usage");
    private static final String START = "prreviewer.start";
    private static final String AGENT = "prreviewer.agent";

    private final CostLedger ledger;

    public CostTrackingListener(CostLedger ledger) {
        this.ledger = ledger;
    }

    @Override
    public void onRequest(ChatModelRequestContext context) {
        context.attributes().put(START, System.nanoTime());
        context.attributes().put(AGENT, AgentAttribution.current());
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        TokenUsage usage = context.chatResponse().tokenUsage();
        String agent = agent(context.attributes().get(AGENT));
        String model = context.chatResponse().modelName() != null
                ? context.chatResponse().modelName()
                : context.chatRequest().modelName();
        long input = usage == null ? 0 : value(usage.inputTokenCount());
        long output = usage == null ? 0 : value(usage.outputTokenCount());
        long cacheRead = 0;
        long cacheWrite = 0;
        if (usage instanceof AnthropicTokenUsage anthropic) {
            cacheRead = value(anthropic.cacheReadInputTokens());
            cacheWrite = value(anthropic.cacheCreationInputTokens());
        } else if (usage instanceof OpenAiTokenUsage openAi && openAi.inputTokensDetails() != null) {
            // OpenAI counts the cached tokens inside the prompt tokens, and charges nothing extra for cache writes
            cacheRead = value(openAi.inputTokensDetails().cachedTokens());
            input = Math.max(0, input - cacheRead);
        }
        ledger.record(agent, model, input, output, cacheRead, cacheWrite);
        if (USAGE.isInfoEnabled()) {
            USAGE.info(String.format(Locale.ROOT,
                    "{\"agent\":\"%s\",\"model\":\"%s\",\"inputTokens\":%d,\"outputTokens\":%d,\"cacheReadTokens\":%d,"
                            + "\"cacheWriteTokens\":%d,\"costUsd\":%.6f,\"millis\":%d,\"outcome\":\"ok\"}",
                    agent, model, input, output, cacheRead, cacheWrite,
                    PriceTable.cost(model, input, output, cacheRead, cacheWrite), millis(context.attributes().get(START))));
        }
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        if (USAGE.isInfoEnabled()) {
            USAGE.info(String.format(Locale.ROOT,
                    "{\"agent\":\"%s\",\"model\":\"%s\",\"millis\":%d,\"outcome\":\"error\",\"error\":\"%s\"}",
                    agent(context.attributes().get(AGENT)), context.chatRequest().modelName(),
                    millis(context.attributes().get(START)),
                    String.valueOf(context.error().getMessage()).replace("\\", "\\\\").replace("\"", "\\\"")));
        }
    }

    private static String agent(Object attribute) {
        return attribute instanceof String s ? s : AgentAttribution.current();
    }

    private static long millis(Object start) {
        return start instanceof Long nanos ? (System.nanoTime() - nanos) / 1_000_000 : -1;
    }

    private static long value(Integer count) {
        return count == null ? 0 : count;
    }
}
