package dev.langchain4j.prreviewer.web;

import dev.langchain4j.prreviewer.model.ReviewReport;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Locale;
import org.jboss.resteasy.reactive.RestPath;

/** Past reports, rendered, and their three files. */
@Path("/reports")
public class ReportResource {

    @Inject
    ReportHistory history;

    @GET
    @Path("{directory}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance report(@RestPath String directory) {
        ReviewReport report = history.report(directory).orElseThrow(() -> new NotFoundException("No report " + directory));
        String markdown = history.file(directory, "report.md").map(file -> {
            try {
                return Files.readString(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).orElse(report.markdown());
        return Pages.report(new Views.ReportPage(directory, report.pullRequest(), report.verdict().name(),
                String.format(Locale.ROOT, "%.1f", report.scores().overall()),
                String.format(Locale.ROOT, "%.1f", report.scores().confidence()),
                report.run() == null ? "" : String.format(Locale.ROOT, "%.2f", report.run().costUsd()),
                MarkdownRenderer.render(markdown), history.file(directory, "trace.html").isPresent()));
    }

    /**
     * The files of a review. The trace is generated HTML that contains pull request text, so it runs sandboxed in an
     * opaque origin: its scripts work, but cannot reach this application.
     */
    @GET
    @Path("{directory}/{file}")
    public Response file(@RestPath String directory, @RestPath String file) {
        java.nio.file.Path path = history.file(directory, file).orElseThrow(() -> new NotFoundException(file));
        return switch (file) {
            case "trace.html" -> Response.ok(path.toFile(), MediaType.TEXT_HTML)
                    .header("Content-Security-Policy", "sandbox allow-scripts")
                    .build();
            case "report.json" -> Response.ok(path.toFile(), MediaType.APPLICATION_JSON).build();
            default -> Response.ok(path.toFile(), MediaType.TEXT_PLAIN + ";charset=utf-8").build();
        };
    }
}
