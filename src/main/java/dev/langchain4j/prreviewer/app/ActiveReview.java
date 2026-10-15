package dev.langchain4j.prreviewer.app;

import dev.langchain4j.prreviewer.hitl.ReviewerConsole;
import dev.langchain4j.prreviewer.observability.CostLedger;
import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

/**
 * The review in progress. Reviews run one at a time, so the per-review state lives in an application-scoped bean,
 * reachable from every thread the agents run on, parallel branches included. Static agent methods reach it through
 * {@link #get()}.
 */
@ApplicationScoped
@Unremovable
public class ActiveReview {

    private ReviewRun run;

    public static ActiveReview get() {
        return Arc.container().instance(ActiveReview.class).get();
    }

    public synchronized void start(ReviewRun run) {
        if (this.run != null) {
            throw new IllegalStateException("A review is already running: " + this.run.request().pr());
        }
        this.run = run;
    }

    public synchronized void end() {
        this.run = null;
    }

    public synchronized Optional<ReviewRun> current() {
        return Optional.ofNullable(run);
    }

    /** The ledger of the review in progress; outside a review, an empty ledger that is never exceeded. */
    public CostLedger ledger() {
        return current().map(ReviewRun::ledger).orElseGet(CostLedger::new);
    }

    public ReviewerConsole console() {
        return current().map(ReviewRun::console)
                .orElseThrow(() -> new IllegalStateException("No reviewer console: interactive mode is not available"));
    }

    public void publish(RunEvent event) {
        current().ifPresent(r -> r.events().accept(event));
    }
}
