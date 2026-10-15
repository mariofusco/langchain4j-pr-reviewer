package dev.langchain4j.prreviewer.keys;

import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.Critique;
import dev.langchain4j.prreviewer.model.FileChange;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.RawFinding;
import dev.langchain4j.prreviewer.model.ReviewMode;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.model.UsefulnessAssessment;
import java.util.List;
import java.util.Map;

/**
 * The AgenticScope keys: the contract between the agents of the reviewer.
 * <p>
 * The name of each key is the simple name of its class, which is also the name of the variable in prompt templates,
 * for instance {@code {{DiffText}}}. Keys that some step may legitimately leave unset have a default value, so their
 * readers never fail with a missing argument.
 */
public final class Keys {

    private Keys() {}

    /** Name of the key written by the human clarification agent (declarative human-in-the-loop takes a String key). */
    public static final String REVIEWER_GUIDANCE = "ReviewerGuidance";

    /** Name of the key written by the human approval agent. */
    public static final String APPROVAL = "Approval";

    // inputs

    public static class PrUrl implements TypedKey<String> {}

    public static class Mode implements TypedKey<ReviewMode> {}

    public static class Options implements TypedKey<ReviewOptions> {}

    // fetching and preprocessing

    public static class PullRequest implements TypedKey<dev.langchain4j.prreviewer.model.PullRequest> {}

    public static class Changes implements TypedKey<List<FileChange>> {}

    /** The reviewed diff, rendered with file headers and new-file line numbers, within the token budget. */
    public static class DiffText implements TypedKey<String> {}

    public static class DiffStats implements TypedKey<dev.langchain4j.prreviewer.model.DiffStats> {}

    /** Parts of the diff left out of {@link DiffText} by the size limits, reviewed separately in chunks. */
    public static class DiffChunks implements TypedKey<List<String>> {
        @Override
        public List<String> defaultValue() {
            return List.of();
        }
    }

    /** One chunk of omitted diff, the item of the chunked review. */
    public static class DiffChunk implements TypedKey<String> {}

    /** Title, author, description, commits, linked issue, CI status and diff statistics, rendered for prompts. */
    public static class PrOverview implements TypedKey<String> {}

    // triage, context and clarification

    public static class PrSummary implements TypedKey<dev.langchain4j.prreviewer.model.PrSummary> {}

    /** The JSON-like input of the reviewer router. */
    public static class RoutingInput implements TypedKey<Map<String, Object>> {}

    public static class CodeContext implements TypedKey<String> {
        @Override
        public String defaultValue() {
            return "";
        }
    }

    public static class ClarificationQuestions implements TypedKey<List<String>> {
        @Override
        public List<String> defaultValue() {
            return List.of();
        }
    }

    public static class ReviewerGuidance implements TypedKey<String> {
        @Override
        public String defaultValue() {
            return "";
        }
    }

    /** The shared prefix of every reviewer prompt: overview, triage, context, guidance and diff. */
    public static class ReviewBundle implements TypedKey<String> {}

    public static class Usefulness implements TypedKey<UsefulnessAssessment> {}

    // review panel

    /** The router's result: reviewer name to findings, empty when no reviewer reached the threshold. */
    public static class PanelOutputs implements TypedKey<Map<String, Object>> {
        @Override
        public Map<String, Object> defaultValue() {
            return Map.of();
        }
    }

    public static class CorrectnessFindings implements TypedKey<List<RawFinding>> {
        @Override
        public List<RawFinding> defaultValue() {
            return List.of();
        }
    }

    public static class SecurityFindings implements TypedKey<List<RawFinding>> {
        @Override
        public List<RawFinding> defaultValue() {
            return List.of();
        }
    }

    public static class DesignFindings implements TypedKey<List<RawFinding>> {
        @Override
        public List<RawFinding> defaultValue() {
            return List.of();
        }
    }

    public static class TestFindings implements TypedKey<List<RawFinding>> {
        @Override
        public List<RawFinding> defaultValue() {
            return List.of();
        }
    }

    public static class DocsFindings implements TypedKey<List<RawFinding>> {
        @Override
        public List<RawFinding> defaultValue() {
            return List.of();
        }
    }

    /** Findings of the chunked review, one list per chunk. */
    public static class ChunkFindings implements TypedKey<List<List<RawFinding>>> {
        @Override
        public List<List<RawFinding>> defaultValue() {
            return List.of();
        }
    }

    // findings and debates

    /** All validated findings, including those rejected by debates or dropped by the reviewer. */
    public static class Findings implements TypedKey<List<Finding>> {
        @Override
        public List<Finding> defaultValue() {
            return List.of();
        }
    }

    /** The findings that still count: neither rejected nor dropped. */
    public static class ActiveFindings implements TypedKey<List<Finding>> {
        @Override
        public List<Finding> defaultValue() {
            return List.of();
        }
    }

    /** Queue of findings waiting for a debate. */
    public static class ContestedFindings implements TypedKey<List<Finding>> {
        @Override
        public List<Finding> defaultValue() {
            return List.of();
        }
    }

    public static class ContestedFinding implements TypedKey<Finding> {}

    /** The rendered changes of the file the contested finding points at, what the debaters read. */
    public static class ContestedDiff implements TypedKey<String> {}

    public static class Prosecution implements TypedKey<String> {}

    public static class Defense implements TypedKey<String> {}

    public static class DebateVerdict implements TypedKey<dev.langchain4j.prreviewer.model.DebateVerdict> {}

    // scoring

    public static class ScoreCard implements TypedKey<dev.langchain4j.prreviewer.model.ScoreCard> {}

    public static class ScoreVote1 implements TypedKey<dev.langchain4j.prreviewer.model.ScoreCard> {}

    public static class ScoreVote2 implements TypedKey<dev.langchain4j.prreviewer.model.ScoreCard> {}

    public static class ScoreVote3 implements TypedKey<dev.langchain4j.prreviewer.model.ScoreCard> {}

    public static class Scores implements TypedKey<dev.langchain4j.prreviewer.model.Scores> {}

    public static class Verdict implements TypedKey<dev.langchain4j.prreviewer.model.Verdict> {}

    public static class VerdictReasoning implements TypedKey<String> {}

    // report and approval

    public static class ReportDraft implements TypedKey<dev.langchain4j.prreviewer.model.ReportDraft> {
        @Override
        public dev.langchain4j.prreviewer.model.ReportDraft defaultValue() {
            return dev.langchain4j.prreviewer.model.ReportDraft.none();
        }
    }

    public static class ReviewReport implements TypedKey<dev.langchain4j.prreviewer.model.ReviewReport> {}

    public static class ReportCritique implements TypedKey<Critique> {
        @Override
        public Critique defaultValue() {
            return Critique.none();
        }
    }

    public static class Approval implements TypedKey<ApprovalDecision> {}

    /** Number of completed contest rounds. */
    public static class RevisionRound implements TypedKey<Integer> {
        @Override
        public Integer defaultValue() {
            return 0;
        }
    }

    public static class FinalReport implements TypedKey<dev.langchain4j.prreviewer.model.ReviewReport> {}

    // bookkeeping

    /** Limitations found by deterministic steps (truncation, skipped reviewers, cost cap). */
    public static class Limitations implements TypedKey<List<String>> {
        @Override
        public List<String> defaultValue() {
            return List.of();
        }
    }

    /** Agents whose failure was recovered with a fallback value. */
    public static class DegradedAgents implements TypedKey<List<String>> {
        @Override
        public List<String> defaultValue() {
            return List.of();
        }
    }
}
