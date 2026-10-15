package dev.langchain4j.prreviewer.web;

import dev.langchain4j.prreviewer.app.ReviewOutcome;
import dev.langchain4j.prreviewer.app.ReviewRequest;
import dev.langchain4j.prreviewer.app.ReviewService;
import dev.langchain4j.prreviewer.hitl.ReviewerConsole;
import dev.langchain4j.prreviewer.model.ReviewMode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reviews submitted through the web interface, run one at a time in submission order by a single worker thread. An
 * interactive review waiting for the reviewer holds the worker; only queued reviews can be cancelled.
 */
@ApplicationScoped
public class ReviewQueue {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewQueue.class);

    @Inject
    ReviewService reviews;

    private final Map<String, WebRun> runs = Collections.synchronizedMap(new LinkedHashMap<>());
    private final LinkedBlockingDeque<WebRun> queue = new LinkedBlockingDeque<>();
    private Thread worker;

    @PostConstruct
    void start() {
        worker = Thread.ofPlatform().name("review-worker").daemon().start(this::work);
    }

    @PreDestroy
    void stop() {
        worker.interrupt();
    }

    public WebRun submit(ReviewRequest request) {
        WebRun run = new WebRun(UUID.randomUUID().toString().substring(0, 8), request);
        runs.put(run.id(), run);
        queue.add(run);
        return run;
    }

    public Optional<WebRun> run(String id) {
        return Optional.ofNullable(runs.get(id));
    }

    /** The runs of this server, newest first. */
    public List<WebRun> runs() {
        List<WebRun> all;
        synchronized (runs) {
            all = new ArrayList<>(runs.values());
        }
        Collections.reverse(all);
        return all;
    }

    /** Cancels a queued review; a running one goes on. */
    public boolean cancel(String id) {
        WebRun run = runs.get(id);
        if (run != null && queue.remove(run)) {
            run.finish(WebRun.Status.CANCELLED, null, null);
            return true;
        }
        return false;
    }

    private void work() {
        while (!Thread.currentThread().isInterrupted()) {
            WebRun run;
            try {
                run = queue.take();
            } catch (InterruptedException e) {
                return;
            }
            execute(run);
        }
    }

    private void execute(WebRun run) {
        run.status(WebRun.Status.RUNNING);
        try {
            ReviewerConsole console = run.request().mode() == ReviewMode.INTERACTIVE ? new WebReviewerConsole(run) : null;
            ReviewOutcome outcome = reviews.review(run.request(), console, run::progress);
            run.finish(outcome.written() ? WebRun.Status.DONE : WebRun.Status.REJECTED, outcome, null);
        } catch (RuntimeException e) {
            LOG.warn("Review of {} failed", run.pr(), e);
            run.finish(WebRun.Status.FAILED, null, rootMessage(e));
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
