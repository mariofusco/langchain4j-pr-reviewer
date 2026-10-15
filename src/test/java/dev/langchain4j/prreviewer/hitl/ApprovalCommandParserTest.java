package dev.langchain4j.prreviewer.hitl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.prreviewer.model.ApprovalDecision.Decision;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApprovalCommandParserTest {

    static final Set<String> IDS = Set.of("F1", "F2", "F3");

    @Test
    void parses_every_command() {
        assertThat(parse("approve").orElseThrow().decision()).isEqualTo(Decision.APPROVED);
        assertThat(parse("edit please thank the author").orElseThrow())
                .isEqualTo(new ApprovalDecision(Decision.EDIT, "please thank the author", List.of(), List.of()));
        assertThat(parse("reject not useful").orElseThrow().decision()).isEqualTo(Decision.REJECTED);
        assertThat(parse("contest F1, f3 the null check is required").orElseThrow()).isEqualTo(new ApprovalDecision(
                Decision.CONTEST, "the null check is required", List.of("F1", "F3"), List.of()));
        assertThat(parse("contest F1 drop F2 F3 noise").orElseThrow()).isEqualTo(new ApprovalDecision(
                Decision.CONTEST, "noise", List.of("F1"), List.of("F2", "F3")));
        assertThat(parse("drop F2").orElseThrow().droppedFindingIds()).containsExactly("F2");
    }

    @Test
    void rejects_unknown_ids_and_incomplete_commands() {
        assertThat(parse("contest F9")).isEmpty();
        assertThat(parse("contest")).isEmpty();
        assertThat(parse("edit")).isEmpty();
        assertThat(parse("merge it")).isEmpty();
        assertThat(parse("  ")).isEmpty();
    }

    @Test
    void console_asks_again_until_understood_and_refuses_debates_past_the_cost_cap() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StdinReviewerConsole console = new StdinReviewerConsole(
                new BufferedReader(new StringReader("what?\ncontest F1\ndrop F1\n")), new PrintStream(out), false);

        var f1 = new dev.langchain4j.prreviewer.model.Finding("F1", "r",
                dev.langchain4j.prreviewer.model.FindingCategory.DESIGN, dev.langchain4j.prreviewer.model.Severity.LOW,
                "A.java", 1, "t", "e", "s", "x", 0.5, dev.langchain4j.prreviewer.model.Verification.UNVERIFIED);
        ApprovalDecision decision = console.askApproval(report(), List.of(f1), 2.5, 2.0, false);

        assertThat(decision.droppedFindingIds()).containsExactly("F1");
        assertThat(out.toString()).contains("Not understood").contains("can no longer be debated");
    }

    @Test
    void end_of_input_approves_only_when_allowed() {
        StdinReviewerConsole lenient = new StdinReviewerConsole(new BufferedReader(new StringReader("")),
                new PrintStream(new ByteArrayOutputStream()), true);
        assertThat(lenient.askApproval(report(), List.of(), 0, 2, true).decision()).isEqualTo(Decision.APPROVED);

        StdinReviewerConsole strict = new StdinReviewerConsole(new BufferedReader(new StringReader("")),
                new PrintStream(new ByteArrayOutputStream()), false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> strict.askApproval(report(), List.of(), 0, 2, true))
                .hasMessageContaining("--approve-on-eof");
    }

    private static Optional<ApprovalDecision> parse(String answer) {
        return ApprovalCommandParser.parse(answer, IDS);
    }

    private static dev.langchain4j.prreviewer.model.ReviewReport report() {
        return new dev.langchain4j.prreviewer.model.ReviewReport("o/r#1", "t", "", List.of(), List.of(), List.of(),
                null, null, "", 0, List.of(), null, "# report");
    }
}
