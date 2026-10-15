package dev.langchain4j.prreviewer.keys;

import dev.langchain4j.prreviewer.model.Critique;
import dev.langchain4j.prreviewer.model.ReportDraft;
import java.util.List;
import java.util.Map;

/**
 * The AgenticScope keys: the contract between the agents of the reviewer.
 * <p>
 * Each key is a String, which is also the name of the agent parameters that read it, and so of the variable in prompt
 * templates, for instance {@code String diffText} and {@code {{diffText}}}; the comment after it gives the type of its
 * value. Keys that some step may legitimately leave unset have a value in {@link #DEFAULTS}, seeded by the root before
 * every review, so their readers never fail with a missing argument.
 */
public final class Keys {

    private Keys() {}

    // inputs

    public static final String PR_URL = "prUrl"; // String

    public static final String MODE = "mode"; // ReviewMode

    public static final String OPTIONS = "options"; // ReviewOptions

    // fetching and preprocessing

    public static final String PULL_REQUEST = "pullRequest"; // PullRequest

    public static final String CHANGES = "changes"; // List<FileChange>

    /** The reviewed diff, rendered with file headers and new-file line numbers, within the token budget. */
    public static final String DIFF_TEXT = "diffText"; // String

    public static final String DIFF_STATS = "diffStats"; // DiffStats

    /** Parts of the diff left out of {@link #DIFF_TEXT} by the size limits, reviewed separately in chunks. */
    public static final String DIFF_CHUNKS = "diffChunks"; // List<String>

    /** One chunk of omitted diff, the item of the chunked review. */
    public static final String DIFF_CHUNK = "diffChunk"; // String

    /** Title, author, description, commits, linked issue, CI status and diff statistics, rendered for prompts. */
    public static final String PR_OVERVIEW = "prOverview"; // String

    // triage, context and clarification

    public static final String PR_SUMMARY = "prSummary"; // PrSummary

    /** The JSON-like input of the reviewer router. */
    public static final String ROUTING_INPUT = "routingInput"; // Map<String, Object>

    public static final String CODE_CONTEXT = "codeContext"; // String

    public static final String CLARIFICATION_QUESTIONS = "clarificationQuestions"; // List<String>

    public static final String REVIEWER_GUIDANCE = "reviewerGuidance"; // String

    /** The shared prefix of every reviewer prompt: overview, triage, context, guidance and diff. */
    public static final String REVIEW_BUNDLE = "reviewBundle"; // String

    public static final String USEFULNESS = "usefulness"; // UsefulnessAssessment

    // review panel

    /** The router's result: reviewer name to findings, empty when no reviewer reached the threshold. */
    public static final String PANEL_OUTPUTS = "panelOutputs"; // Map<String, Object>

    public static final String CORRECTNESS_FINDINGS = "correctnessFindings"; // List<RawFinding>

    public static final String SECURITY_FINDINGS = "securityFindings"; // List<RawFinding>

    public static final String DESIGN_FINDINGS = "designFindings"; // List<RawFinding>

    public static final String TEST_FINDINGS = "testFindings"; // List<RawFinding>

    public static final String DOCS_FINDINGS = "docsFindings"; // List<RawFinding>

    /** Findings of the chunked review, one list per chunk. */
    public static final String CHUNK_FINDINGS = "chunkFindings"; // List<List<RawFinding>>

    // findings and debates

    /** All validated findings, including those rejected by debates or dropped by the reviewer. */
    public static final String FINDINGS = "findings"; // List<Finding>

    /** The findings that still count: neither rejected nor dropped. */
    public static final String ACTIVE_FINDINGS = "activeFindings"; // List<Finding>

    /** Queue of findings waiting for a debate. */
    public static final String CONTESTED_FINDINGS = "contestedFindings"; // List<Finding>

    public static final String CONTESTED_FINDING = "contestedFinding"; // Finding

    /** The rendered changes of the file the contested finding points at, what the debaters read. */
    public static final String CONTESTED_DIFF = "contestedDiff"; // String

    public static final String PROSECUTION = "prosecution"; // String

    public static final String DEFENSE = "defense"; // String

    public static final String DEBATE_VERDICT = "debateVerdict"; // DebateVerdict

    // scoring

    public static final String SCORE_CARD = "scoreCard"; // ScoreCard

    public static final String SCORE_VOTE1 = "scoreVote1"; // ScoreCard

    public static final String SCORE_VOTE2 = "scoreVote2"; // ScoreCard

    public static final String SCORE_VOTE3 = "scoreVote3"; // ScoreCard

    public static final String SCORES = "scores"; // Scores

    public static final String VERDICT = "verdict"; // Verdict

    public static final String VERDICT_REASONING = "verdictReasoning"; // String

    // report and approval

    public static final String REPORT_DRAFT = "reportDraft"; // ReportDraft

    public static final String REVIEW_REPORT = "reviewReport"; // ReviewReport

    public static final String REPORT_CRITIQUE = "reportCritique"; // Critique

    public static final String APPROVAL = "approval"; // ApprovalDecision

    /** Number of completed contest rounds. */
    public static final String REVISION_ROUND = "revisionRound"; // Integer

    /** Rounds of the report quality loop in the current revision pass, counted by reportAssembler. */
    public static final String REPORT_ROUND = "reportRound"; // Integer

    public static final String FINAL_REPORT = "finalReport"; // ReviewReport

    // bookkeeping

    /** Limitations found by deterministic steps (truncation, skipped reviewers, cost cap). */
    public static final String LIMITATIONS = "limitations"; // List<String>

    /** Agents whose failure was recovered with a fallback value. */
    public static final String DEGRADED_AGENTS = "degradedAgents"; // List<String>

    /** Values of the keys that some step may legitimately leave unset. */
    public static final Map<String, Object> DEFAULTS = Map.ofEntries(
            Map.entry(DIFF_CHUNKS, List.of()),
            Map.entry(CODE_CONTEXT, ""),
            Map.entry(CLARIFICATION_QUESTIONS, List.of()),
            Map.entry(REVIEWER_GUIDANCE, ""),
            Map.entry(PANEL_OUTPUTS, Map.of()),
            Map.entry(CORRECTNESS_FINDINGS, List.of()),
            Map.entry(SECURITY_FINDINGS, List.of()),
            Map.entry(DESIGN_FINDINGS, List.of()),
            Map.entry(TEST_FINDINGS, List.of()),
            Map.entry(DOCS_FINDINGS, List.of()),
            Map.entry(CHUNK_FINDINGS, List.of()),
            Map.entry(FINDINGS, List.of()),
            Map.entry(ACTIVE_FINDINGS, List.of()),
            Map.entry(CONTESTED_FINDINGS, List.of()),
            Map.entry(REPORT_DRAFT, ReportDraft.none()),
            Map.entry(REPORT_CRITIQUE, Critique.none()),
            Map.entry(REVISION_ROUND, 0),
            Map.entry(REPORT_ROUND, 0),
            Map.entry(LIMITATIONS, List.of()),
            Map.entry(DEGRADED_AGENTS, List.of()));
}
