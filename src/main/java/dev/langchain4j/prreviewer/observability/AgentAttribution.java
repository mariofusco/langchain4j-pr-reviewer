package dev.langchain4j.prreviewer.observability;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tracks which agent is running on the current thread, so that model calls can be attributed to it. The agentic
 * framework notifies listeners on the thread that runs the agent, also for parallel and asynchronous agents.
 */
public final class AgentAttribution implements AgentListener {

    public static final String UNATTRIBUTED = "(unattributed)";

    private static final ThreadLocal<Deque<String>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    public static String current() {
        String agent = STACK.get().peek();
        return agent == null ? UNATTRIBUTED : agent;
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        STACK.get().push(request.agentName());
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        pop(response.agentName());
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        pop(error.agentName());
    }

    private static void pop(String agent) {
        Deque<String> stack = STACK.get();
        if (agent != null && agent.equals(stack.peek())) {
            stack.pop();
        }
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }
}
