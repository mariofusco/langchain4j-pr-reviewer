package dev.langchain4j.prreviewer.web;

import dev.langchain4j.prreviewer.app.ReviewOutcome;
import dev.langchain4j.prreviewer.app.ReviewRequest;
import dev.langchain4j.prreviewer.app.RunEvent;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One review submitted through the web interface: its status, the progress rows so far, what it waits for from the
 * reviewer, and the browsers following it over server-sent events. Events are replayed to a browser that reconnects.
 */
public class WebRun {

    public enum Status {
        QUEUED, RUNNING, WAITING_FOR_YOU, DONE, REJECTED, FAILED, CANCELLED;

        public boolean finished() {
            return this == DONE || this == REJECTED || this == FAILED || this == CANCELLED;
        }

        public String label() {
            return name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }

    /** One finished or failed agent, as the progress table shows it. */
    public record ProgressRow(boolean ok, String agent, String model, String seconds, String costUsd, String error) {

        static ProgressRow of(RunEvent event) {
            return new ProgressRow(event.kind() == RunEvent.Kind.AGENT_FINISHED, event.agent(),
                    event.model() == null ? "" : event.model(),
                    String.format(Locale.ROOT, "%.1f", event.millis() / 1000.0),
                    String.format(Locale.ROOT, "%.4f", event.costUsd()), event.detail());
        }
    }

    private record Subscriber(MultiEmitter<? super OutboundSseEvent> emitter, Sse sse) {}

    private final String id;
    private final ReviewRequest request;
    private final Instant created = Instant.now();
    private final List<ProgressRow> rows = new ArrayList<>();
    private final List<Subscriber> subscribers = new ArrayList<>();
    private Status status = Status.QUEUED;
    private Interaction interaction;
    private String interactionError;
    private ReviewOutcome outcome;
    private String error;
    private double costUsd;

    WebRun(String id, ReviewRequest request) {
        this.id = id;
        this.request = request;
    }

    public String id() {
        return id;
    }

    public ReviewRequest request() {
        return request;
    }

    public String pr() {
        return request.pr().toString();
    }

    public Instant created() {
        return created;
    }

    public synchronized Status status() {
        return status;
    }

    public synchronized List<ProgressRow> rows() {
        return List.copyOf(rows);
    }

    public synchronized Interaction interaction() {
        return interaction;
    }

    public synchronized String interactionError() {
        return interactionError;
    }

    public synchronized ReviewOutcome outcome() {
        return outcome;
    }

    public synchronized String error() {
        return error;
    }

    public synchronized String costUsd() {
        return String.format(Locale.ROOT, "%.4f", costUsd);
    }

    /** The name of the report's directory, once a report was written. */
    public synchronized String reportDirectory() {
        return outcome != null && outcome.written() ? outcome.outputDir().getFileName().toString() : null;
    }

    void progress(RunEvent event) {
        if (event.kind() == RunEvent.Kind.AGENT_STARTED) {
            return;
        }
        ProgressRow row = ProgressRow.of(event);
        synchronized (this) {
            rows.add(row);
            costUsd = event.costUsd();
        }
        emit("progress", Fragments.progressRow(row));
    }

    void status(Status status) {
        synchronized (this) {
            this.status = status;
        }
        emit("status", status.name());
    }

    void waitFor(Interaction interaction) {
        synchronized (this) {
            this.interaction = interaction;
            this.interactionError = null;
            this.status = Status.WAITING_FOR_YOU;
        }
        emit("interaction", "");
        emit("status", Status.WAITING_FOR_YOU.name());
    }

    synchronized void refuse(String error) {
        this.interactionError = error;
    }

    void answered() {
        synchronized (this) {
            this.interaction = null;
            this.interactionError = null;
            this.status = Status.RUNNING;
        }
        emit("interaction", "");
        emit("status", Status.RUNNING.name());
    }

    void finish(Status status, ReviewOutcome outcome, String error) {
        synchronized (this) {
            this.status = status;
            this.outcome = outcome;
            this.error = error;
            this.interaction = null;
        }
        emit("status", status.name());
        emit("done", "");
        List<Subscriber> current;
        synchronized (this) {
            current = List.copyOf(subscribers);
            subscribers.clear();
        }
        current.forEach(s -> s.emitter().complete());
    }

    /** Follows the run from the given number of progress rows, which the page already shows. */
    void subscribe(MultiEmitter<? super OutboundSseEvent> emitter, Sse sse, int fromRow) {
        Subscriber subscriber = new Subscriber(emitter, sse);
        synchronized (this) {
            for (int i = Math.max(0, fromRow); i < rows.size(); i++) {
                send(subscriber, "progress", Fragments.progressRow(rows.get(i)));
            }
            send(subscriber, "status", status.name());
            if (status.finished()) {
                send(subscriber, "done", "");
                emitter.complete();
                return;
            }
            subscribers.add(subscriber);
        }
        emitter.onTermination(() -> {
            synchronized (this) {
                subscribers.remove(subscriber);
            }
        });
    }

    private void emit(String name, String data) {
        List<Subscriber> current;
        synchronized (this) {
            current = List.copyOf(subscribers);
        }
        current.forEach(s -> send(s, name, data));
    }

    private static void send(Subscriber subscriber, String name, String data) {
        subscriber.emitter().emit(subscriber.sse().newEventBuilder().name(name).data(data.isEmpty() ? name : data).build());
    }
}
