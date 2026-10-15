package dev.langchain4j.prreviewer.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The command line, with scripted models, the fake decision model and the GitHub fixtures of pull request 6610. */
@QuarkusMainTest
@TestProfile(PrReviewerCliTest.Keys.class)
class PrReviewerCliTest {

    /** Placeholder keys, so that both providers count as available; the models are scripted anyway. */
    public static class Keys implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("ANTHROPIC_API_KEY", "test", "OPENAI_API_KEY", "test");
        }
    }

    @Test
    void help_lists_the_commands(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("review").contains("serve").contains("eval").contains("completion");
    }

    @Test
    void review_help_groups_the_options_and_shows_the_configured_defaults(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("review", "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("Models").contains("Budgets")
                .contains("(default: anthropic)").contains("(default: 2.0)");
    }

    @Test
    void completion_prints_a_bash_script(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("completion");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("_complete_pr-reviewer").contains("review serve eval completion");
    }

    @Test
    void review_prints_the_summary_and_writes_the_report(QuarkusMainLauncher launcher) throws Exception {
        Path out = Path.of("target/cli-test-output");

        LaunchResult result = launcher.launch("review", "langchain4j/langchain4j#6610", "--out", out.toString(),
                "--quiet");

        assertThat(result.exitCode()).as(result.getErrorOutput()).isZero();
        assertThat(result.getOutput()).contains("langchain4j/langchain4j#6610: ").contains("overall")
                .contains("Report and trace in");
        assertThat(Files.readString(out.resolve("langchain4j-langchain4j-6610/report.md"))).contains("**Verdict:**");
    }

    @Test
    void progress_goes_to_stderr(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("review", "langchain4j/langchain4j#6610", "--out", "target/cli-test-output");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getErrorOutput()).contains("prTriage").contains("reportWriter");
    }

    @Test
    void invalid_input_is_a_usage_error(QuarkusMainLauncher launcher) {
        assertThat(launcher.launch("review").exitCode()).isEqualTo(2);
        assertThat(launcher.launch("review", "not-a-pr").exitCode()).isEqualTo(2);
        LaunchResult threshold = launcher.launch("review", "owner/repo#1", "--router-threshold", "1.5");
        assertThat(threshold.exitCode()).isEqualTo(2);
        assertThat(threshold.getErrorOutput()).contains("--router-threshold must be between 0 and 1");
    }
}
