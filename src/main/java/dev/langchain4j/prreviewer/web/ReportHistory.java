package dev.langchain4j.prreviewer.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.prreviewer.config.ReviewerConfig;
import dev.langchain4j.prreviewer.model.ReviewReport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The reports in the output directory: the history survives restarts and needs no database. Only the three files a
 * review writes can be read, from directories named like a review's, so no request can reach any other file.
 */
@ApplicationScoped
public class ReportHistory {

    static final Set<String> FILES = Set.of("report.md", "report.json", "trace.html");
    private static final Pattern DIRECTORY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*-\\d+");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** One past review. */
    public record Entry(String directory, String pr, String verdict, String overall, Instant modified, String when,
                        boolean hasTrace) {}

    @Inject
    ReviewerConfig config;

    /** The most recent reviews first. */
    public List<Entry> entries(int limit) {
        Path root = config.outputDir();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                entry(dir).ifPresent(entries::add);
            }
        } catch (IOException e) {
            return List.of();
        }
        entries.sort(Comparator.comparing(Entry::modified).reversed());
        return entries.stream().limit(limit).toList();
    }

    public Optional<ReviewReport> report(String directory) {
        return file(directory, "report.json").flatMap(json -> {
            try {
                return Optional.of(JSON.readValue(json.toFile(), ReviewReport.class));
            } catch (IOException e) {
                return Optional.empty();
            }
        });
    }

    /** A file of a review, only when both names are valid and the file exists. */
    public Optional<Path> file(String directory, String name) {
        if (directory == null || !DIRECTORY.matcher(directory).matches() || !FILES.contains(name)) {
            return Optional.empty();
        }
        Path root = config.outputDir().toAbsolutePath().normalize();
        Path file = root.resolve(directory).resolve(name).normalize();
        return file.startsWith(root) && Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    private Optional<Entry> entry(Path dir) {
        String name = dir.getFileName().toString();
        return report(name).map(report -> new Entry(name, report.pullRequest(), report.verdict().name(),
                String.format(java.util.Locale.ROOT, "%.1f", report.scores().overall()), modified(dir.resolve("report.json")),
                WHEN.format(modified(dir.resolve("report.json"))), Files.exists(dir.resolve("trace.html"))));
    }

    private static Instant modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }
}
