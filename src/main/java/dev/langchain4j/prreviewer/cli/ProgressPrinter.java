package dev.langchain4j.prreviewer.cli;

import dev.langchain4j.prreviewer.app.RunEvent;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.function.Consumer;
import picocli.CommandLine.Help.Ansi;

/** One line per finished or failed agent, on stderr: agent, model, duration and the cost of the review so far. */
final class ProgressPrinter implements Consumer<RunEvent> {

    private final PrintWriter err;
    private final Ansi ansi;

    ProgressPrinter(PrintWriter err, Ansi ansi) {
        this.err = err;
        this.ansi = ansi;
    }

    @Override
    public synchronized void accept(RunEvent event) {
        String status = switch (event.kind()) {
            case AGENT_STARTED -> null;
            case AGENT_FINISHED -> "@|green ok|@  ";
            case AGENT_FAILED -> "@|red fail|@";
        };
        if (status == null) {
            return;
        }
        String line = String.format(Locale.ROOT, "  %s %-30s %-28s %7.1fs  $%.4f", status, event.agent(),
                event.model() == null ? "" : event.model(), event.millis() / 1000.0, event.costUsd());
        if (event.kind() == RunEvent.Kind.AGENT_FAILED && event.detail() != null) {
            line += "  @|faint " + event.detail().replace("|", "/") + "|@";
        }
        err.println(ansi.string(line));
        err.flush();
    }
}
