package dev.langchain4j.prreviewer.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.AppContext;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.PrRef;
import dev.langchain4j.prreviewer.model.ReviewReport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Non-AI agent, last of the system: renders the final report with the final cost, and writes {@code report.md} and
 * {@code report.json}, unless the reviewer rejected it. Nothing is ever written to GitHub.
 */
public class ReportExporter {

    private static final Logger LOG = LoggerFactory.getLogger(ReportExporter.class);
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Agent(name = "reportExporter", description = "Writes the final report to report.md and report.json",
            typedOutputKey = Keys.FinalReport.class)
    public static ReviewReport export(AgenticScope scope,
                                      @K(Keys.ReviewReport.class) ReviewReport draftReport,
                                      @K(Keys.Approval.class) ApprovalDecision approval) {
        ReviewReport report = ReportAssembler.assemble(scope);
        if (approval.decision() == ApprovalDecision.Decision.REJECTED) {
            LOG.info("The reviewer rejected the report: nothing is written");
            return report;
        }
        Path dir = outputDirectory(scope.readState(Keys.PullRequest.class).ref());
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("report.md"), report.markdown(), StandardCharsets.UTF_8);
            JSON.writeValue(dir.resolve("report.json").toFile(), report);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the report to " + dir, e);
        }
        LOG.info("Report written to {}", dir.toAbsolutePath());
        return report;
    }

    public static Path outputDirectory(PrRef ref) {
        return AppContext.get().outputDir().resolve(ref.owner() + "-" + ref.repo() + "-" + ref.number());
    }
}
