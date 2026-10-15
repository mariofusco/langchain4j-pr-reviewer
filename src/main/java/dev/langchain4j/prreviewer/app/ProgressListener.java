package dev.langchain4j.prreviewer.app;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Publishes the start and the end of every leaf agent to the review in progress, with its duration, its model and the
 * running cost. Listeners are notified on the thread that runs the agent, so a per-thread stack of start times pairs
 * the events, also for parallel branches.
 */
@ApplicationScoped
public class ProgressListener implements AgentListener {

    private static final ThreadLocal<Deque<Long>> STARTS = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject
    ActiveReview activeReview;

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        if (request.agent().leaf()) {
            STARTS.get().push(System.nanoTime());
            activeReview.publish(new RunEvent(RunEvent.Kind.AGENT_STARTED, request.agentName(), null, 0,
                    activeReview.ledger().total(), null));
        }
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        if (response.agent().leaf()) {
            String model = response.chatResponse() == null ? null : response.chatResponse().modelName();
            activeReview.publish(new RunEvent(RunEvent.Kind.AGENT_FINISHED, response.agentName(), model, elapsed(),
                    activeReview.ledger().total(), null));
        }
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        if (error.agent().leaf()) {
            activeReview.publish(new RunEvent(RunEvent.Kind.AGENT_FAILED, error.agentName(), null, elapsed(),
                    activeReview.ledger().total(), String.valueOf(error.error().getMessage())));
        }
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    private static long elapsed() {
        Long start = STARTS.get().poll();
        return start == null ? 0 : (System.nanoTime() - start) / 1_000_000;
    }
}
