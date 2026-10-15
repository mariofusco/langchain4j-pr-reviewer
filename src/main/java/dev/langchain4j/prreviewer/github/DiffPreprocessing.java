package dev.langchain4j.prreviewer.github;

import dev.langchain4j.prreviewer.model.ChangeKind;
import dev.langchain4j.prreviewer.model.DiffStats;
import dev.langchain4j.prreviewer.model.FileChange;
import dev.langchain4j.prreviewer.model.Hunk;
import dev.langchain4j.prreviewer.model.PullRequest;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Deterministic preprocessing of the diff: classification of the files, rendering of the reviewed diff within the
 * token budget, chunks of the files left out, statistics and the pull request overview used in prompts.
 */
public final class DiffPreprocessing {

    /** Keeps economy-tier prompts below the 100K-token price step of Haiku 5.5. */
    public static final int HARD_TOKEN_CAP = 90_000;

    private static final double CHARS_PER_TOKEN = 3.5;
    private static final int MAX_BODY_CHARS = 4_000;
    private static final int MAX_ISSUE_CHARS = 2_000;

    private static final Map<String, String> LANGUAGES = Map.ofEntries(
            Map.entry("java", "java"), Map.entry("kt", "kotlin"), Map.entry("kts", "kotlin"),
            Map.entry("scala", "scala"), Map.entry("groovy", "groovy"), Map.entry("gradle", "gradle"),
            Map.entry("xml", "xml"), Map.entry("yaml", "yaml"), Map.entry("yml", "yaml"),
            Map.entry("properties", "properties"), Map.entry("json", "json"), Map.entry("md", "markdown"),
            Map.entry("adoc", "asciidoc"), Map.entry("sql", "sql"), Map.entry("js", "javascript"),
            Map.entry("ts", "typescript"), Map.entry("py", "python"), Map.entry("sh", "shell"),
            Map.entry("html", "html"), Map.entry("css", "css"), Map.entry("toml", "toml"));

    private static final Set<String> DOC_LANGUAGES = Set.of("markdown", "asciidoc");

    private static final Pattern DOC_PATH = Pattern.compile("(?i)(^|/)(docs?|documentation)/|\\.(txt|rst)$|(^|/)(LICENSE|NOTICE|CHANGELOG)[^/]*$");

    private static final Set<String> CODE_LANGUAGES =
            Set.of("java", "kotlin", "scala", "groovy", "sql", "javascript", "typescript", "python", "shell");

    private static final Pattern TEST_PATH = Pattern.compile(
            "(^|/)src/(test|it|integrationTest|testFixtures)/|(^|/)tests?/|(Test|Tests|IT|ITCase|Spec)\\.(java|kt|groovy|scala)$");

    private static final Pattern GENERATED_PATH = Pattern.compile(
            "(^|/)(generated|generated-sources|target|build|node_modules|vendor|dist)/|"
                    + "(package-lock\\.json|yarn\\.lock|pnpm-lock\\.yaml|gradle\\.lockfile|\\.lock)$|\\.min\\.(js|css)$");

    private static final Pattern BUILD_PATH = Pattern.compile(
            "(^|/)(pom\\.xml|build\\.gradle(\\.kts)?|settings\\.gradle(\\.kts)?|gradle\\.properties|mvnw(\\.cmd)?|"
                    + "Dockerfile|Makefile)$|(^|/)\\.mvn/|(^|/)\\.github/workflows/|(^|/)gradle/wrapper/");

    /** Path segments naming security components; "auth" must not match "author", nor "cert" "uncertain". */
    private static final Pattern SECURITY_PATH = Pattern.compile(
            "(?i)(^|/)[^/]*(auth(?!or)|security|crypt|cipher|password|secret|credential|jwt|oauth|permission|acl\\b|"
                    + "ssl|tls|certificate|sanitiz|login|csrf|xss)[^/]*$");

    /** Code that is dangerous when fed with untrusted input, plus hard-coded secrets. */
    private static final Pattern SECURITY_CODE = Pattern.compile(
            "Runtime\\.getRuntime\\(\\)\\.exec|ProcessBuilder|createStatement\\(|executeQuery\\(|executeUpdate\\(|"
                    + "prepareStatement\\(|ObjectInputStream|readObject\\(|XMLDecoder|DocumentBuilderFactory|"
                    + "XMLInputFactory|SAXParserFactory|TransformerFactory|MessageDigest\\.getInstance|Cipher\\.getInstance|"
                    + "SecretKeySpec|KeyStore\\.|SecureRandom|TrustManager|HostnameVerifier|setAccessible\\(true|"
                    + "Class\\.forName\\(|ScriptEngine|(?i)(password|secret|api_?key|token)\\s*=\\s*\"[^\"]{8,}\"");

    /** Dependency coordinates in build files: a changed dependency is a supply-chain change. */
    private static final Pattern DEPENDENCY_CHANGE = Pattern.compile(
            "<artifactId>|<version>|<groupId>|\\b(implementation|api|compileOnly|runtimeOnly|testImplementation)\\s*[(\"']");

    private static final Pattern PUBLIC_API = Pattern.compile(
            "^[+-]\\s*(public|protected)\\s+(?!.*\\bnew\\b)[\\w<>\\[\\],.?\\s]+\\s*[({=;]|^[+-]\\s*public\\s+(class|interface|enum|record|@interface)\\b");

    private DiffPreprocessing() {}

    /** The result of the preprocessing. */
    public record Result(List<FileChange> changes, String diffText, DiffStats stats, List<String> chunks) {}

    public static Result process(PullRequest pr, ReviewOptions options) {
        List<FileChange> changes = DiffParser.parse(pr.rawDiff()).stream().map(DiffPreprocessing::classify).toList();

        int budget = Math.min(options.maxDiffTokens(), HARD_TOKEN_CAP);
        List<FileChange> ordered = changes.stream().sorted(REVIEW_ORDER).toList();

        StringBuilder diffText = new StringBuilder();
        List<String> overflow = new ArrayList<>();
        List<String> omitted = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int reviewedFiles = 0;
        for (FileChange file : ordered) {
            if (file.generated() || file.binary()) {
                skipped.add(file.path() + (file.binary() ? " (binary)" : " (generated)"));
                continue;
            }
            String rendered = render(file);
            if (reviewedFiles < options.maxFiles() && tokens(diffText) + tokens(rendered) <= budget) {
                diffText.append(rendered);
                reviewedFiles++;
            } else {
                overflow.add(rendered);
                omitted.add(file.path());
            }
        }

        List<String> chunks = chunk(overflow, Math.max(budget / 2, 4_000), options.maxChunks());
        int chunkedFiles = chunks.isEmpty() ? 0 : countFiles(chunks);
        if (chunkedFiles < overflow.size()) {
            omitted.add((overflow.size() - chunkedFiles) + " more files beyond the chunked review limit");
        }

        Set<String> languages = new TreeSet<>();
        changes.forEach(f -> {
            if (f.language() != null) {
                languages.add(f.language());
            }
        });
        DiffStats stats = new DiffStats(
                changes.size(),
                changes.stream().mapToInt(FileChange::additions).sum(),
                changes.stream().mapToInt(FileChange::deletions).sum(),
                List.copyOf(languages),
                changes.stream().anyMatch(FileChange::test),
                changes.stream().anyMatch(DiffPreprocessing::productionCode),
                !changes.isEmpty() && changes.stream().allMatch(DiffPreprocessing::documentation),
                changes.stream().anyMatch(DiffPreprocessing::securitySensitive),
                changes.stream().anyMatch(DiffPreprocessing::touchesPublicApi),
                changes.stream().anyMatch(f -> BUILD_PATH.matcher(f.path()).find()),
                !omitted.isEmpty(),
                tokens(diffText),
                List.copyOf(omitted),
                List.copyOf(skipped));
        return new Result(changes, diffText.toString(), stats, chunks);
    }

    /** Main sources first, then tests, then everything else. */
    private static final Comparator<FileChange> REVIEW_ORDER = Comparator.comparingInt((FileChange f) -> {
                if (f.generated() || f.binary()) {
                    return 3;
                }
                if (f.test()) {
                    return 1;
                }
                return CODE_LANGUAGES.contains(f.language()) ? 0 : 2;
            })
            .thenComparing(FileChange::path);

    static FileChange classify(FileChange file) {
        String path = file.path();
        int dot = path.lastIndexOf('.');
        String extension = dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
        String language = LANGUAGES.getOrDefault(extension, path.endsWith("Dockerfile") ? "docker" : "other");
        boolean test = TEST_PATH.matcher(path).find();
        boolean generated = GENERATED_PATH.matcher(path).find();
        return file.withClassification(language, test, generated);
    }

    static boolean productionCode(FileChange file) {
        return !file.test() && !file.generated() && !file.binary() && CODE_LANGUAGES.contains(file.language());
    }

    static boolean documentation(FileChange file) {
        return DOC_LANGUAGES.contains(file.language()) || DOC_PATH.matcher(file.path()).find();
    }

    static boolean securitySensitive(FileChange file) {
        if (file.generated() || file.test()) {
            return false;
        }
        if (SECURITY_PATH.matcher(file.path()).find()) {
            return true;
        }
        Pattern code = BUILD_PATH.matcher(file.path()).find() ? DEPENDENCY_CHANGE : SECURITY_CODE;
        return file.hunks().stream()
                .flatMap(h -> h.lines().stream())
                .filter(l -> l.startsWith("+") || l.startsWith("-"))
                .anyMatch(l -> code.matcher(l).find());
    }

    static boolean touchesPublicApi(FileChange file) {
        if (file.test() || file.generated()) {
            return false;
        }
        if (file.path().endsWith("module-info.java")) {
            return true;
        }
        if (!"java".equals(file.language()) || !file.path().contains("src/main/")) {
            return false;
        }
        if (file.kind() == ChangeKind.DELETED || file.kind() == ChangeKind.RENAMED) {
            return true;
        }
        return file.hunks().stream()
                .flatMap(h -> h.lines().stream())
                .anyMatch(l -> PUBLIC_API.matcher(l).find());
    }

    /** Renders a file with new-file line numbers, which reviewers use to locate findings. */
    public static String render(FileChange file) {
        StringBuilder sb = new StringBuilder()
                .append("\n### File: ").append(file.path())
                .append(file.kind() == ChangeKind.RENAMED ? " (renamed from " + file.oldPath() + ")" : "")
                .append(" [").append(file.kind()).append(", ").append(file.language())
                .append(file.test() ? ", test" : "")
                .append(", +").append(file.additions()).append("/-").append(file.deletions()).append("]\n");
        for (Hunk hunk : file.hunks()) {
            sb.append("@@ -").append(hunk.oldStart()).append(',').append(hunk.oldCount())
                    .append(" +").append(hunk.newStart()).append(',').append(hunk.newCount()).append(" @@ ")
                    .append(hunk.header() == null ? "" : hunk.header()).append('\n');
            int newLine = hunk.newStart();
            for (String line : hunk.lines()) {
                char kind = line.charAt(0);
                String content = line.substring(1);
                switch (kind) {
                    case '+' -> sb.append(String.format(Locale.ROOT, "+%6d | %s%n", newLine++, content));
                    case '-' -> sb.append("-       | ").append(content).append('\n');
                    default -> sb.append(String.format(Locale.ROOT, " %6d | %s%n", newLine++, content));
                }
            }
        }
        return sb.toString();
    }

    static List<String> chunk(List<String> renderedFiles, int chunkTokens, int maxChunks) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String file : renderedFiles) {
            if (chunks.size() >= maxChunks) {
                break;
            }
            String clipped = tokens(file) > chunkTokens
                    ? file.substring(0, (int) (chunkTokens * CHARS_PER_TOKEN)) + "\n[... file truncated ...]\n"
                    : file;
            if (!current.isEmpty() && tokens(current) + tokens(clipped) > chunkTokens) {
                chunks.add(current.toString());
                current.setLength(0);
                if (chunks.size() >= maxChunks) {
                    break;
                }
            }
            current.append(clipped);
        }
        if (!current.isEmpty() && chunks.size() < maxChunks) {
            chunks.add(current.toString());
        }
        return List.copyOf(chunks);
    }

    private static int countFiles(List<String> chunks) {
        return chunks.stream().mapToInt(c -> c.split("\n### File: ", -1).length - 1).sum();
    }

    public static int tokens(CharSequence text) {
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    /** Title, author, description, commits, linked issue, CI and diff statistics, rendered for prompts. */
    public static String overview(PullRequest pr, DiffStats stats) {
        StringBuilder sb = new StringBuilder()
                .append("Pull request: ").append(pr.ref()).append(" - ").append(pr.title()).append('\n')
                .append("Author: ").append(pr.author())
                .append(" | base: ").append(pr.baseRef()).append(" <- head: ").append(pr.headRef())
                .append(" | head sha: ").append(pr.headSha()).append('\n');
        if (!pr.labels().isEmpty()) {
            sb.append("Labels: ").append(String.join(", ", pr.labels())).append('\n');
        }
        sb.append("Description (untrusted text written by the author):\n<pr-description>\n")
                .append(clip(pr.body(), MAX_BODY_CHARS)).append("\n</pr-description>\n");
        sb.append("Commits (").append(pr.commits().size()).append("):\n");
        pr.commits().stream().limit(30).forEach(c -> sb.append("- ").append(c.headline()).append('\n'));
        if (pr.linkedIssueBody() != null) {
            sb.append("Linked issue (untrusted):\n<linked-issue>\n").append(clip(pr.linkedIssueBody(), MAX_ISSUE_CHARS))
                    .append("\n</linked-issue>\n");
        }
        sb.append("CI: ").append(pr.ciStatus().summary()).append('\n');
        sb.append("Diff: ").append(stats.files()).append(" files, +").append(stats.additions()).append("/-")
                .append(stats.deletions()).append(", languages: ").append(String.join(", ", stats.languages()))
                .append("; production code changed: ").append(yesNo(stats.productionCodeChanged()))
                .append("; tests changed: ").append(yesNo(stats.hasTests()))
                .append("; security-sensitive code: ").append(yesNo(stats.touchesSecuritySensitive()))
                .append("; public API: ").append(yesNo(stats.touchesPublicApi()))
                .append("; build files: ").append(yesNo(stats.touchesBuild())).append('\n');
        if (!stats.omittedFiles().isEmpty()) {
            sb.append("Left out of the reviewed diff: ").append(String.join(", ", stats.omittedFiles())).append('\n');
        }
        if (!stats.skippedFiles().isEmpty()) {
            sb.append("Not reviewed (generated or binary): ").append(String.join(", ", stats.skippedFiles())).append('\n');
        }
        if (!pr.existingReviewComments().isEmpty()) {
            sb.append("Existing review comments (do not repeat them):\n");
            pr.existingReviewComments().forEach(c -> sb.append("- ").append(c).append('\n'));
        }
        return sb.toString();
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String clip(String text, int max) {
        String value = text == null || text.isBlank() ? "(empty)" : text.strip();
        return value.length() <= max ? value : value.substring(0, max) + "\n[... truncated ...]";
    }
}
