# LangChain4j PR Reviewer — Plan

*Revision 6, 2026-10-20: GitHub is read through the hub4j client of the `quarkus-github-api` extension instead of the
GitHub MCP server; the agents get their tools with `@ToolBox`, their budget from a tool executor wrapper. Planned in
`GITHUB-API-PLAN.md`, implemented on the `github-api` branch; the notes are in section 21.5.*

*Revision 5, 2026-10-15: the reviewer moves to Quarkus through the quarkus-langchain4j extensions, gains a local web
interface, and its CLI becomes a set of picocli subcommands with shell completion. Implemented the same day on the
`quarkus-port` branch; what the implementation changed in this plan is listed in section 21.4. Revision 4 (2026-10-08) described the
plain-Java implementation that exists today; its design carries over, and the notes it accumulated are kept in
section 22. Decisions in section 0 come from the grilling rounds of 2026-10-15.*

*Target stack: Java 21, Quarkus 3.40, quarkus-langchain4j 1.15.x (1.15.0.CR1 until the final release), which brings
langchain4j 1.21.0 and the beta modules (`langchain4j-agentic`, `-agentic-patterns`, `-typesafe`) at
1.21.0-beta31. Every agentic API the reviewer uses exists in that version: `DecisionRouterPlanner` and the typesafe
module first shipped in 1.21.0-beta31, and the agentic modules are identical in 1.21 and 1.22.*

---

## 0. Decisions

### 0.1 Reviewer design (unchanged since revision 4)

| Decision | Choice |
|---|---|
| Purpose | Daily tool for GitHub repositories, mostly Java. Precision and low noise first; the design shows the agentic module well. |
| Orchestration | Declarative API throughout; deterministic sequence root; no supervisor. |
| Reviewer selection | `DecisionRouterPlanner` (threshold mode) on a TypeSafe decision model (local Ollama `tev1:0.8b` by default, hosted TypeSafe or OpenRouter), plus a deterministic coverage floor. |
| Cost ceiling | Soft cap (default $2): past it, voting, new debates and model escalation are skipped and the report says so; the review always completes. |
| Model selection | Scope-driven `@ChatModelSupplier(AgenticScope)`: a small, medium or large model per agent, escalated on evidence. |
| Voting and debate | Voting only for borderline score cards; debate only for contested findings. Both gated and capped. |
| Verdict and scores | The model proposes; rules only lower scores and make the verdict stricter. |
| Code access | GitHub only, through the hub4j client of `quarkus-github-api`, read-only. No clone, build or test execution. |
| Human in the loop | Synchronous: clarify before the review; approve, edit, contest or drop findings, or reject after it. |
| Output | `report.md`, `report.json` and the monitor trace `trace.html`. Never writes to GitHub. |

### 0.2 Quarkus port, web interface, CLI (2026-10-15)

| Area | Decision | Consequence |
|---|---|---|
| Versions | quarkus-langchain4j 1.15.x on Quarkus 3.40, langchain4j 1.21.0 | Two 1.22-only features are parked (section 21): Anthropic automatic prompt caching and the OpenAI decision model. |
| Agent construction | quarkus-langchain4j-agentic builds the system; `PrReviewSystem` is `@Inject`ed | The app adapts to the extension's build-time validation (section 5.3): string keys, declaring projections, `AgenticScope` reads, a round counter instead of `@LoopCounter`. |
| Collaborators | Idiomatic CDI, no global `AppContext` | CDI parameters where the extension resolves them, `@ApplicationScoped` non-AI agents, `Arc` lookups elsewhere (section 6.2). |
| Per-review state | `@ApplicationScoped` `ActiveReview` bean | Safe because reviews run one at a time; reachable from every thread, including parallel branches. |
| Models | Only from `application.properties`, as named configurations | Anthropic per-tier effort is dropped (`EffortHttpClientBuilder` goes away); OpenAI keeps `reasoning-effort` per tier. |
| Per-review options | Provider, decision model and router threshold are per-review options with configured defaults | A ConfigSource disables providers whose key is missing, so the app starts with any subset of keys. |
| Tool limits | Moved into a `BudgetedToolProvider` | Same budgets (12, 6, 6); tool errors now reach the model instead of failing the agent. |
| Module layout | One Maven module | JVM only; no native image. |
| CLI | `pr-reviewer review <PR>`, `serve`, `eval run`, `eval build-set`, `completion` | The `--pr` form and the `eval/*.sh` scripts go away. |
| Completion | bash/zsh script from picocli's `GenerateCompletion` | Static candidates: subcommands, options, enum values, paths. |
| Web users | Local, single user, one review at a time (FIFO queue) | Bound to 127.0.0.1, no login, server-side keys. |
| Web scope | Form, live progress over SSE, clarification and approval in the browser, rendered report, downloads, history | Qute + htmx (mvnpm), no JavaScript build. |
| Tests | Pure logic stays plain JUnit; system, CLI and web tests are `@QuarkusTest` with test producers | The stub sub-agent tests become scripted-model tests. |
| Distribution | Quarkus uber-jar plus a JBang alias | `jbang-catalog.json` points to the locally installed runner jar until the project is published. |

---

## 1. Goal and scope

**Input.** A GitHub pull request (`https://github.com/{owner}/{repo}/pull/{n}` or `owner/repo#n`), a mode (`AUTO` or
`INTERACTIVE`) and options: provider, decision model, router threshold, deep mode, forced model tier, size limits,
debate cap, cost cap.

**Output.** A `ReviewReport` with what is good, what is wrong and what could be improved (each code item anchored to
`file:line`), scores from 0.0 to 10.0, a verdict (`APPROVE`, `COMMENT`, `REQUEST_CHANGES`) with its reasoning, written
to `report.md` and `report.json`, plus `trace.html`.

**Entry points.** One application, two front ends over the same `ReviewService`:

- the CLI (`pr-reviewer review owner/repo#123`), with the console as the human channel;
- the local web interface (`pr-reviewer serve`), with the browser as the human channel.

**Non-goals.** Writing to GitHub; cloning or building; non-GitHub forges; multi-user or remote deployment of the web
interface (login, per-user tokens); concurrent reviews; resuming a review after a restart; native executables.

---

## 2. Design principles

1. **Deterministic sequence outside, model judgment inside.** The only model-driven orchestration decisions are
   reviewer selection, debate convergence and voting, each bounded and gated by Java predicates.
2. **One concern per agent, typed outputs.** Records, enums, lists and numeric scores; no text parsing. Scope keys are
   strings (section 5), but every value behind them is typed.
3. **Non-AI agents for deterministic work**: fetching, diff parsing, consolidation, human-feedback application,
   calibration, rendering, export.
4. **Cheapest model that does the job, escalated by evidence in the scope.**
5. **Expensive patterns only when strictly necessary.** Voting on borderline cards, debate on contested findings, capped.
6. **The model owns the judgment, the rules own the floor.**
7. **Human gates are ordinary agents guarded by `mode`**; defaults are seeded so no key is missing.
8. **Read-only by construction.** A `GitHubReads` interface without write methods, five read tools, a read-only token, no agent that writes to GitHub.
9. **PR content is untrusted** in prompts, in findings (validated against the diff) and now in the browser (escaped).
10. **Observed from day one.** Monitored root, HTML trace, cost ledger per agent and model, live progress.
11. **Framework-managed wiring.** Models, decision models, tools and listeners come from configuration and CDI;
    the agentic system is built and validated by the Quarkus extension.

---

## 3. Architecture

```
pr-reviewer (Quarkus uber-jar, @QuarkusMain)
│   adds the "cli" profile unless the command is serve: no HTTP server, no banner, WARN logs
│
├─ picocli top command ── review <PR> · serve · eval run · eval build-set · completion
│        │                    │
│        │              web: Qute + htmx pages, SSE progress, forms      (quarkus-rest, 127.0.0.1)
│        └────────┬───────────┘
│                 ▼
│         ReviewService (@ApplicationScoped)
│           CLI: runs the review on the calling thread
│           web: FIFO queue, one worker thread
│           per review: ActiveReview.start(run) → monitor cleared → system.review(...) → trace.html → end
│                 │
│                 ▼
│         PrReviewSystem (@Inject; built and validated by quarkus-langchain4j-agentic)
│           static suppliers and conditions reach CDI beans: ModelCatalog, DecisionModels, GitHubReads, ActiveReview
│                 │
│                 ▼
│   quarkus-langchain4j: anthropic · openai (named chat models) · typesafe (named decision models)
│   quarkus-github-api: the hub4j client (GitHub), behind GitHubReads; GitHubTools (@ToolBox) and ToolBudget (executor wrapper)
│   CDI listeners: CostTrackingListener (ChatModelListener), DecisionCostListener (DecisionModelListener),
│                  AgentAttribution and ProgressListener (AgentListener)
```

**`ActiveReview`** (`@ApplicationScoped`, `@Unremovable`) holds the running `ReviewRun`: id, request, options, cost
ledger, human channel (`ReviewerConsole`), event sink, output directory. `start` refuses a second concurrent review.
Every piece of code that needs per-review state reads it here: model and planner suppliers, gates, human-in-the-loop
agents, listeners, the exporter.

**`ReviewService`** turns a `ReviewRequest` (PR, mode, options) into a `ReviewRun`. The run executes in a method
annotated `@ActivateRequestContext` (Quarkus' default memory-id provider needs a request context when an agent is
invoked outside HTTP). It clears the monitor before each review (`agentMonitor().clear()`), so `trace.html` covers one
review and a long-running server does not accumulate executions.

---

## 4. Topology

`(new)` marks the nodes added by the port. Model sizes: small, medium, large (tiers ECONOMY, STANDARD, PREMIUM).

```
prReviewSystem  @SequenceAgent, root, MonitoredAgent; @BeforeCall seeds defaults; @ErrorHandler for the whole tree
├─ prFetcher              non-AI bean      prUrl → pullRequest (hub4j, plus one REST call for the diff)
├─ diffPreprocessor       non-AI bean      → diffStats; also writes changes, diffText, prOverview, diffChunks
├─ diffText               projection (new) → diffText
├─ prOverview             projection (new) → prOverview
├─ diffChunks             projection (new) → diffChunks
├─ prTriage               small            → prSummary
├─ routingInputs          non-AI bean      → routingInput (flags for the router)
├─ contextGate            @ConditionalAgent [triage needs context]
│    └─ contextGatherer   small → medium, GitHub read tools (12 calls)   → codeContext
├─ clarificationGate      @ConditionalAgent [INTERACTIVE]
│    └─ clarification     @SequenceAgent: questionGenerator (small) → questionsGate → humanClarification   → reviewerGuidance
├─ reviewBundleBuilder    non-AI bean      → reviewBundle (what every reviewer reads)
├─ usefulnessAssessor     small → medium, asynchronous   → usefulness
├─ reviewPanel            @PlannerAgent + DecisionRouterPlanner   → panelOutputs
│    ├─ correctnessReviewer   medium → large     ├─ testReviewer      small
│    ├─ securityReviewer      medium → large     └─ docsApiReviewer   small
│    └─ designReviewer        small → medium
├─ coverageFloor          @ConditionalAgent: correctnessReviewerFallback (medium), securityReviewerFallback (medium),
│                         docsApiReviewerFallback (small), each when the router skipped an essential review
├─ diffChunk              declaration (new) → diffChunk (the mapper passes each chunk directly)
├─ largeDiffGate          @ConditionalAgent [diffChunks not empty]
│    └─ chunkedReview     @ParallelMapperAgent over diffChunks: chunkReviewer (medium)   → chunkFindings
├─ findingsConsolidator   non-AI bean      → findings; also writes contestedFindings, limitations
├─ reviewRevisionLoop     @LoopAgent, at most 3 passes, exits unless the approval is CONTEST
│    ├─ applyHumanFeedback      non-AI bean
│    ├─ debateGate              @ConditionalAgent [contested findings, within the cost cap]
│    │    └─ contestedDebates   @LoopAgent, one finding per iteration
│    │         ├─ nextContested        non-AI bean   → contestedFinding; also writes contestedDiff
│    │         ├─ contestedDiff        projection (new) → contestedDiff
│    │         ├─ debateContext        projection (new) → debateContext (the debate planner's transcript)
│    │         ├─ findingDebate        @PlannerAgent + DebatePlanner(2 rounds, unanimousLastWord)   → debateVerdict
│    │         │    ├─ findingProsecutor   medium, GitHub read tools (6 calls)
│    │         │    ├─ findingDefender     medium, GitHub read tools (6 calls)
│    │         │    └─ debateJudge         medium → large
│    │         └─ applyDebateVerdict   non-AI bean   → findings
│    ├─ debateBudgetGuard       non-AI bean   leftover contested findings become UNSURE
│    ├─ activeFindingsSelector  non-AI bean   → activeFindings
│    ├─ scorer                  medium        → scoreCard
│    ├─ votingGate              @ConditionalAgent [borderline, within the cost cap]
│    │    └─ scoreVoting        @PlannerAgent + VotingPlanner: neutralScorer (medium), skepticalScorer (medium), lenientScorer (small)
│    ├─ scoreCalibrator         non-AI bean   → scores; also writes verdict, verdictReasoning
│    ├─ verdict                 projection (new) → verdict
│    ├─ verdictReasoning        projection (new) → verdictReasoning
│    ├─ limitations             projection (new) → limitations
│    ├─ reportQualityLoop       @LoopAgent, at most 3 rounds: reportWriter (medium → large), reportAssembler (non-AI), reportCritic (small)
│    └─ approvalGate            @ConditionalAgent: humanApproval [INTERACTIVE] | autoApproval [AUTO]   → approval
├─ finalizerGate          @ConditionalAgent [edits requested, or contest after the last pass]
│    └─ finalization      @SequenceAgent: applyFinalFeedback, scoreCalibrator, reportFinalizer (small), reportAssembler
└─ reportExporter         non-AI bean      report.md, report.json (not on rejection)   → finalReport
```

The revision loop is what lets the human contest findings: after a `CONTEST` decision, `applyHumanFeedback` drops the
dropped findings and queues the contested ids, then debate, scoring, writing and approval run again. `AUTO` exits after
the first pass; two contest rounds at most.

A **projection** is a non-AI agent that returns the value its producer already wrote to the scope, under the same key.
It changes nothing at run time; it gives the key a declared producer, which the Quarkus validation requires for every
parameter an agent reads (section 5.3), and it makes the data flow visible in the topology and the trace.

---

## 5. Scope keys

### 5.1 String keys

`Keys` becomes a class of `String` constants, named after the former `TypedKey` classes, whose values are the camelCase
key names (`Keys.DIFF_TEXT = "diffText"`). Agent parameters carry no `@V`: the code is compiled with `-parameters`, and
both the extension's validation and langchain4j take the key from the parameter name. So every parameter is named after
the key it reads, and prompt templates use the same names (`{{diffText}}`, `{{prOverview}}`, …). Mechanical rewrite:

| Today | After the port |
|---|---|
| `typedOutputKey = Keys.Findings.class` | `outputKey = Keys.FINDINGS` |
| `@K(Keys.PrSummary.class) PrSummary summary` | `PrSummary prSummary` |
| `{{PrSummary}}` in a prompt template | `{{prSummary}}` |
| `scope.readState(Keys.Findings.class)` | `scope.readState(Keys.FINDINGS, List.of())` |
| `TypedKey.defaultValue()` | seeded by the root's `@BeforeCall` with `writeStateIfAbsent` (section 5.2) |
| `ScopeLists.append(scope, Keys.Limitations.class, …)` | `ScopeLists.append(scope, Keys.LIMITATIONS, …)` |

### 5.2 Defaults seeded by the root

`@BeforeCall static void seedDefaults(AgenticScope scope)` on `PrReviewSystem` (the extension requires exactly an
`AgenticScope` parameter) writes, if absent: `diffChunks`, `clarificationQuestions`, `correctnessFindings`,
`securityFindings`, `designFindings`, `testFindings`, `docsFindings`, `chunkFindings`, `findings`, `activeFindings`,
`contestedFindings`, `limitations`, `degradedAgents` (empty lists); `codeContext`, `reviewerGuidance` (`""`);
`panelOutputs` (empty map); `reportDraft` (`ReportDraft.none()`); `reportCritique` (`Critique.none()`);
`revisionRound` and `reportRound` (`0`). These are exactly the keys with a default today, plus `reportRound`.

### 5.3 Adapting to the extension's validation

At build time the extension checks every parameter of every agent method and `@ExitCondition`: its name must be the
`outputKey` of some agent, with the same raw type, or a parameter of an `@Inject`ed root agent. `AgenticScope`,
`InvocationParameters` and `@MemoryId` parameters are exempt. `typedOutputKey`, `@K` and `@LoopCounter` are not
understood, and keys written with `scope.writeState` count as undeclared. Inventory of today's code:

| Key or construct | Today | Readers that fail the check | Adaptation |
|---|---|---|---|
| all 47 typed keys | `typedOutputKey` (56 uses), `@K` | every agent | string keys (5.1) |
| `diffText` | written by diffPreprocessor | prTriage, contextGatherer (AI), reviewBundleBuilder | `diffText` projection |
| `prOverview` | written by diffPreprocessor | 12 AI agents, reviewBundleBuilder | `prOverview` projection |
| `diffChunks` | written by diffPreprocessor | chunkedReview, largeDiffGate (method parameters) | `diffChunks` projection |
| `contestedDiff` | written by nextContested | findingProsecutor, findingDefender (AI) | `contestedDiff` projection |
| `verdict`, `verdictReasoning` | written by scoreCalibrator | reportWriter (AI), reportAssembler | `verdict`, `verdictReasoning` projections; reportAssembler reads them through `AgenticScope` (it also runs in finalization, after the projections) |
| `limitations` | appended by five agents | reportWriter (AI), reportAssembler | `limitations` projection before the report loop; reportAssembler reads through `AgenticScope` |
| `changes` | written by diffPreprocessor | findingsConsolidator, nextContested (non-AI) | `AgenticScope` parameter |
| `contestedFindings` | written by four agents | contestedDebates (method parameter), debateBudgetGuard, nextContested | `AgenticScope` reads; `contestedDebates` takes `findings` as its method parameter |
| `@LoopCounter` | reportQualityLoop's exit condition | the exit condition | `reportRound` counter incremented by reportAssembler, reset by applyHumanFeedback; the exit condition reads it through `AgenticScope` |
| `debateContext` | written by the debate planner | findingProsecutor, findingDefender, debateJudge (AI) | `debateContext` projection, after nextContested resets it |
| `diffChunk` (mapper item) | chunkReviewer's parameter | chunkReviewer | `diffChunk` declaration before largeDiffGate; the mapper passes each chunk to the reviewer directly, so the empty value it writes is never read |

Activation conditions are not validated, so `debateGate`'s condition may keep its key parameters. The spike (section
20) runs the build and confirms the list is complete.

### 5.4 Key table

| Key | Type | Declared by | Also written by | Main readers |
|---|---|---|---|---|
| `prUrl`, `mode`, `options` | `String`, `ReviewMode`, `ReviewOptions` | root parameters | — | prFetcher; gates; suppliers, gates, preprocessor |
| `pullRequest` | `PullRequest` | prFetcher | — | preprocessor, humanClarification, assembler, exporter |
| `diffStats` | `DiffStats` | diffPreprocessor | — | routingInputs, coverageFloor, calibrator, voting gate, suppliers |
| `changes` | `List<FileChange>` | — (scope) | diffPreprocessor | consolidator, nextContested |
| `diffText`, `prOverview`, `diffChunks` | `String`, `String`, `List<String>` | projections | diffPreprocessor | triage, gatherer, bundle builder, AI agents; chunked review |
| `prSummary` | `PrSummary` | prTriage | — | most AI agents, routingInputs, gates, calibrator |
| `routingInput` | `Map<String, Object>` | routingInputs | — | reviewPanel (the decision input) |
| `codeContext` | `String` | contextGatherer, contextGate | — | questionGenerator, bundle builder |
| `clarificationQuestions` | `List<String>` | questionGenerator | — | questionsGate, humanClarification |
| `reviewerGuidance` | `String` | humanClarification and its gates | applyHumanFeedback | bundle builder, debaters, scorers, writer |
| `reviewBundle` | `String` | reviewBundleBuilder | — | the reviewers and their fallbacks |
| `usefulness` | `UsefulnessAssessment` | usefulnessAssessor | — | scorers, writer |
| `panelOutputs` | `Map<String, Object>` | reviewPanel | — | coverageFloor, consolidator, assembler |
| `correctnessFindings` … `docsFindings`, `chunkFindings` | `List<RawFinding>`, `List<List<RawFinding>>` | reviewers, fallbacks, chunked review | — | consolidator |
| `findings` | `List<Finding>` | consolidator, debate steps, applyHumanFeedback | applyFinalFeedback | debate steps, active selector, humanApproval |
| `activeFindings` | `List<Finding>` | activeFindingsSelector, applyFinalFeedback | — | scorers, calibrator, writer, critic, assembler |
| `contestedFindings` | `List<Finding>` | — (scope) | consolidator, nextContested, budget guard, applyHumanFeedback | debate gate and loop, budget guard |
| `contestedFinding`, `contestedDiff` | `Finding`, `String` | nextContested, projection | nextContested | debaters, judge, applyDebateVerdict |
| `prosecution`, `defense`, `debateVerdict` | `String`, `String`, `DebateVerdict` | debaters, judge | nextContested (reset) | the debate, applyDebateVerdict |
| `scoreCard`, `scoreVote1..3` | `ScoreCard` | scorer, voters, scoreVoting | — | voting gate, calibrator |
| `scores`, `verdict`, `verdictReasoning` | `Scores`, `Verdict`, `String` | calibrator, projections | calibrator | writer, assembler, report loop |
| `reportDraft`, `reviewReport`, `reportCritique` | `ReportDraft`, `ReviewReport`, `Critique` | writer/finalizer, assembler, critic | applyHumanFeedback (reset) | the report loop, approval, exporter |
| `approval` | `ApprovalDecision` | approval agents | applyHumanFeedback (reset) | revision loop exit, feedback steps, finalizer gate |
| `revisionRound`, `reportRound` | `Integer` | — (scope) | applyHumanFeedback; reportAssembler | assembler; report loop exit |
| `limitations`, `degradedAgents` | `List<String>` | projection; — | five agents; error handler | writer, assembler, calibrator |
| `finalReport` | `ReviewReport` | reportExporter (root output) | — | the caller |
| `attempts.<agent>` | `Integer` | — (scope) | error handler | error handler |

---

## 6. Agents under quarkus-langchain4j-agentic

### 6.1 How the system is built

The extension scans the agent annotations at build time, generates a bean per agent interface and builds the system at
runtime with `AgenticServices.createAgenticSystem`, so every declarative feature the reviewer uses (`@PlannerSupplier`,
`@ToolProviderSupplier`, `@ErrorHandler`, `@HumanInTheLoop`, `@ExitCondition`, `@ActivationCondition`, `@BeforeCall`,
`@ParallelMapperAgent`, async agents) is handled by langchain4j itself. `ReviewService` injects `PrReviewSystem`.
Leaf agents are resolved from CDI once, when the root is built.

Changes to the agent definitions besides the keys:

- `@ChatModelSupplier` and `@ModelName` are mutually exclusive; the reviewer uses suppliers only.
- The root's `@AgentListenerSupplier` goes away: the extension adds every `AgentListener` bean to every agent.
- `PrReviewSystems` (the hand-written builder with its `AgentConfigurator`) is deleted; the tool budgets it set move to
  `BudgetedToolProvider` (6.4).

### 6.2 Reaching collaborators

The extension resolves CDI parameters (marked `@CdiBean` or with a CDI qualifier) only on some static methods. For the
others the reviewer looks beans up through `Arc`; those beans are `@Unremovable`, because the build cannot see the
lookups.

| Method kind | Gets collaborators through |
|---|---|
| `@ChatModelSupplier` (per call; once at build time with every argument null) | `@CdiBean ModelCatalog` and `AgenticScope` parameters; at build time, the base tier from an `Arc` lookup |
| `@ToolBox(GitHubTools.class)` on the agent method | the extension resolves the `GitHubTools` singleton, into which `GitHubReads` is injected |
| `@PlannerSupplier` (no-arg; once at build time and once per review) | `Arc`: `DecisionModels` and `ActiveReview`; configured defaults at build time |
| `@ActivationCondition`, `@ExitCondition` | scope parameters, plus `Arc` for `ActiveReview` (cost cap) |
| `@ErrorHandler` | `ErrorContext` only; per-review data comes from its scope |
| `@HumanInTheLoop` static methods | `Arc`: `ActiveReview.console()` |
| non-AI agents | static `@Agent` methods with `Arc` lookups (6.3) |
| listeners | they are CDI beans themselves |

### 6.3 Non-AI agents stay static

The plan was to turn non-AI agents into `@ApplicationScoped` beans with instance `@Agent` methods. The Phase 0 spike
showed that langchain4j builds a class whose `@Agent` method is not static as an AI service, which Quarkus then refuses
("not able to determine class as an AiService at build time"). They keep static methods and reach beans through
`Arc` (`GitHubReads.get()`, `ActiveReview.get()`, `ReviewDefaults.get()`); those beans are `@Unremovable`.

### 6.4 Tools and their budgets

`GitHubTools` is a `@Singleton` bean with five `@Tool` methods delegating to `GitHubReads`: `get_file_contents`,
`search_code`, `get_commit`, `list_commits` and `issue_read`, the names the prompts use (they were the MCP server's).
The context gatherer, the prosecutor and the defender declare `@ToolBox(GitHubTools.class)`; the agentic extension
hands the bean to the agent and Quarkus runs the tools through the executors it generated at build time (the bean is
a singleton because the extension looks the tool metadata up by the class of the instance; a client proxy would also be
found, through a subclass check).

`ToolBudget` is a `QuarkusToolExecutor.Wrapper` bean: Quarkus applies it to every tool call with the invocation
context of the AI service call, whose `invocationId` is one agent invocation with its whole tool loop and whose
`interfaceName` is the agent interface. It counts the GitHub tool calls per invocation id (contextGatherer 12,
prosecutor and defender 6 each) and, past the budget, answers "Tool budget exhausted: answer with what you have, without
calling tools" without reading GitHub. The round trips of an invocation are bounded separately by the extension:
`quarkus.langchain4j.ai-service.max-tool-executions=16` (default 10, which the budget of 12 could never reach; this
deprecated property is the one the agents read, not `max-tool-calling-round-trips`).

A tool that fails, because the model guessed a wrong path or sha, or because GitHub refused the request, reaches the
model as text, the extension's default for tool errors, so the model corrects itself; a tool argument of the wrong
type fails the invocation, and the error handler substitutes the agent's fallback. Code search returns file paths
without the matching lines: hub4j has no support for the text-match media type.

### 6.5 Monitoring and trace

`PrReviewSystem` still extends `MonitoredAgent`. Before each review `ReviewService` clears the monitor; after it,
`HtmlReportGenerator.generateReport(system.agentMonitor(), dir.resolve("trace.html"))`. The injected root is a client
proxy: `ClientProxy.unwrap` it before `HtmlReportGenerator.generateTopology`. In dev mode, the Dev UI shows the topology.

---

## 7. Models and configuration

### 7.1 Named chat models

Every model is a named configuration `<provider>-<tier>`, plus OpenAI variants for tool-using agents:

| Tier (size) | Anthropic | OpenAI | OpenAI tool variant |
|---|---|---|---|
| ECONOMY (small) | `anthropic-economy`: claude-haiku-5-5, 16K tokens | `openai-economy`: gpt-5-nano, effort `low`, 16K | — (gpt-5-nano has no `none` effort) |
| STANDARD (medium) | `anthropic-standard`: claude-sonnet-5-5, 32K | `openai-standard`: gpt-5.6-luna, effort `medium`, 64K | `openai-standard-tools`: effort `none` |
| PREMIUM (large) | `anthropic-premium`: claude-opus-5-5, 32K | `openai-premium`: gpt-5.1, effort `high`, 64K | `openai-premium-tools`: effort `none` |

All with a 10-minute timeout. Anthropic: thinking blocks returned and sent back (`thinking.return-thinking`,
`thinking.send-thinking`), `cache-system-messages` and `cache-tools`, no effort setting. OpenAI: strict JSON schemas.
Native structured output: the configuration offers the JSON schema capability only together with a JSON response
format, which would also apply to agents returning text, so `StructuredOutput` declares the capability with a
`ModelBuilderCustomizer` per named model (a named model only gets customizers qualified with its name).
Every named model also sets `quarkus.langchain4j.<name>.chat-model.provider`, because a model referenced only from a
static supplier is not an injection point and would otherwise not be created.

```properties
# defaults of the review options; CLI flags and the web form override them
reviewer.provider=anthropic
reviewer.decision-model=local
# router threshold: empty means 0.2 for the local decision model, 0.5 otherwise
reviewer.router-threshold=
reviewer.mode=auto
reviewer.max-cost=2.0
reviewer.max-debates=3
reviewer.max-files=50
reviewer.max-diff-tokens=60000
reviewer.max-chunks=4
reviewer.output-dir=review-output

# one block per tier and provider; the economy Anthropic block as an example
quarkus.langchain4j.anthropic-economy.chat-model.provider=anthropic
quarkus.langchain4j.anthropic.anthropic-economy.api-key=${ANTHROPIC_API_KEY:dummy}
quarkus.langchain4j.anthropic.anthropic-economy.timeout=10m
quarkus.langchain4j.anthropic.anthropic-economy.chat-model.model-name=claude-haiku-5-5
quarkus.langchain4j.anthropic.anthropic-economy.chat-model.max-tokens=16000
quarkus.langchain4j.anthropic.anthropic-economy.chat-model.thinking.return-thinking=true
quarkus.langchain4j.anthropic.anthropic-economy.chat-model.thinking.send-thinking=true
quarkus.langchain4j.anthropic.anthropic-economy.chat-model.cache-system-messages=true
quarkus.langchain4j.anthropic.anthropic-economy.chat-model.cache-tools=true

quarkus.langchain4j.openai-standard-tools.chat-model.provider=openai
quarkus.langchain4j.openai.openai-standard-tools.api-key=${OPENAI_API_KEY:dummy}
quarkus.langchain4j.openai.openai-standard-tools.timeout=10m
quarkus.langchain4j.openai.openai-standard-tools.chat-model.model-name=gpt-5.6-luna
quarkus.langchain4j.openai.openai-standard-tools.chat-model.reasoning-effort=none
quarkus.langchain4j.openai.openai-standard-tools.chat-model.max-completion-tokens=64000
```

The exact keys (per-name API key and timeout, structured-output settings) are confirmed in Phase 0 against the 1.15
config classes.

### 7.2 Selection

`ModelCatalog` (`@ApplicationScoped`) returns `chatModel(provider, tier)` and `toolModel(provider, tier)` (the tool
variant when configured, else the plain model) through `@Any Instance<ChatModel>` and `ModelName.Literal`.
`ModelSelection` keeps its rule: a forced tier (`--model-tier`) wins; otherwise the base tier, escalated when the
scope shows the trigger, unless the cost cap is reached. The provider comes from `options` in the scope.

| Agent | Base → escalated | Trigger |
|---|---|---|
| contextGatherer | small → medium | risk ≥ MEDIUM |
| usefulnessAssessor | small → medium | FEATURE or REFACTOR |
| designReviewer | small → medium | LARGE or REFACTOR |
| correctnessReviewer | medium → large | HIGH risk, LARGE, or `--deep` |
| securityReviewer | medium → large | security-sensitive change, or `--deep` |
| debateJudge | medium → large | the contested finding is CRITICAL |
| reportWriter | medium → large | the critic criticized the previous draft |

Fixed small: prTriage, questionGenerator, testReviewer, docsApiReviewer, docsApiReviewerFallback, lenientScorer,
reportCritic, reportFinalizer. Fixed medium: scorer, neutralScorer, skepticalScorer, chunkReviewer, findingProsecutor,
findingDefender, correctnessReviewerFallback, securityReviewerFallback.

### 7.3 Missing keys and the GitHub token

A named Anthropic or OpenAI model fails application startup when its key is missing, and a hosted typesafe decision
model does the same. Simpler than the `enable-integration` switch first planned, every key has a placeholder default
(`${ANTHROPIC_API_KEY:missing}`), so startup always succeeds:

- `ModelCatalog.available(provider)` and `DecisionModels.available(provider)` check that the key is really set: the
  web form offers only those, and the CLI rejects the others with a message naming the missing variable;
- a call that still reaches a provider with the placeholder fails as an account problem and stops the run;
- `ReviewerConfigSource` provides `github.token`: `GITHUB_TOKEN`, else the output of `gh auth token`; when neither
  exists, `HubGitHubReads` refuses to build the client and says how to set a token. The client is built on first use,
  so `completion` and `--help` never reach GitHub.

### 7.4 Decision models

Named typesafe configurations, all with a decision-cost listener bean:

| Name | Base URL | Model | Key |
|---|---|---|---|
| `local` | `${SYSTEM_ONE_BASE_URL:http://localhost:11434}` | `${SYSTEM_ONE_MODEL_NAME:tev1:0.8b}` | none |
| `hosted` | default (`https://api.typesafe.ai`) | `${TYPESAFE_MODEL_NAME:jev-latest}` | `TYPESAFE_API_KEY` |
| `openrouter` | `https://openrouter.ai/api/` | `${OPENROUTER_DECISION_MODEL:typesafe/jev-1.13}` | `OPENROUTER_API_KEY` |

`DecisionModels` (`@ApplicationScoped`) selects one by name. `reviewPanel`'s `@PlannerSupplier` runs once per review,
so it builds `new DecisionRouterPlanner(decisionModels.get(options.decisionModel()), options.routerThreshold())` from
`ActiveReview`. `--decision-model openai` is parked (section 21).

### 7.5 GitHub client

`io.quarkiverse.githubapi:quarkus-github-api:1.330.0` brings hub4j 1.330 (`org.kohsuke:github-api`) and its
native-image substitutions; at this version it provides no CDI bean and no configuration. `HubGitHubReads`
(`@ApplicationScoped`, the `GitHubReads` bean) builds the client on first use with `GitHubBuilder`: the token of
`github.token`, and the `FAIL` rate-limit and abuse-limit handlers, so a refused request fails instead of sleeping until
the limit resets (hub4j's default waits up to an hour). Repositories and the fetched pull request are cached in the
bean, so the lists of files, commits, reviews and comments reuse the pull request object. There is no property to set:
the client needs the token only.

---

## 8. GitHub access

`GitHubReads` is the seam: typed methods returning small records (`PullRequestData`, `ChangedFile`, `CommitInfo`,
`CheckRun`, `Review`, `ReviewComment`, `Issue`, `Contents`, `CodeMatch`, `PullRequestSummary`), implemented by
`HubGitHubReads` over hub4j and, in tests, by `FixtureGitHubReads` over recorded REST responses. hub4j objects never
cross it. `prFetcher` reads the pull request through `PullRequestReader`: metadata, the unified diff, commits, check
runs of the head sha, reviews and review comments, and the linked issue. hub4j has no call for the unified diff, so
`unifiedDiff` is one direct REST call (`Accept: application/vnd.github.diff`, 60 s timeout) that returns empty on HTTP
406, when GitHub refuses a diff above its size limits; the reader then rebuilds it from the per-file patches, as before.
Review comments arrive flat with `in_reply_to_id` and are grouped under their root comment; the resolved state of a
thread, which only GraphQL exposes, is no longer shown. A `GitHubAccessException` carries the HTTP status of a refusal,
and the error handler retries a rate-limited request (403 with a limit message, or 429) once. The tools of section 6.4
are the only GitHub access offered to AI agents; prompts tell them to read files at the head sha. The token should be a
read-only fine-grained PAT; nothing but the interface and the token prevents a write.

---

## 9. Reviewer selection

- `routingInputs` renders the triage and the diff statistics into compact flags (production code changed,
  security-sensitive code, public API, documentation only, …); each reviewer's description states the flags it needs.
- `DecisionRouterPlanner` asks one yes/no question per reviewer in a single decision call; every reviewer at or above
  the threshold runs in parallel.
- `coverageFloor` then runs the essential reviewers the router skipped: correctness when production code changed,
  security when security-sensitive code changed, documentation for documentation-only changes.
- Measured on five synthetic changes (25 decisions): hosted TypeSafe 25/25 at 0.5; local `tev1:0.8b` 22/25 at its best
  threshold and 17/25 at 0.5, hence 0.2 as the local default. Topic-style descriptions got 13/25 locally.
- If the decision model fails, the error handler returns an empty map and the floor runs.

---

## 10. Voting and debate

**Voting** runs only for a borderline score card within the cost cap: the capped overall within 7.0 ± 0.5, or a
proposed verdict one step more lenient than the rule floor when the deciding finding's confidence is between 0.5 and
0.8. `scoreVoting` runs three scorers in parallel; the strategy averages the metrics and takes the majority verdict.

**Debate** runs for contested findings: HIGH or CRITICAL with confidence below 0.8, or contradicted by another
reviewer at the same location, or contested by the human; at most `--max-debates` (3) per revision, highest severity
first. Debates run sequentially in `contestedDebates`; prosecutor and defender argue with read-only tools on the
changes of the finding's file only, the judge decides last (`CONFIRMED`, `REJECTED` or `UNSURE`, possibly lowering the
severity). Past the cost cap, `debateBudgetGuard` marks the remaining contested findings `UNSURE` and adds a
limitation.

---

## 11. Scoring and verdict

Metrics 0.0–10.0 (usefulness 0.20, correctness 0.30, conciseness 0.10, security 0.15, test coverage 0.10,
maintainability 0.10, API and docs 0.05, PR hygiene reported but not weighted), `null` when not applicable, overall
as the renormalized weighted mean, confidence 0.0–1.0. The rubric lives in `prompts/scoring-rubric.md`.

`scoreCalibrator` applies caps and the verdict floor; it never raises a score or relaxes a verdict:

| Rule | Effect |
|---|---|
| credible CRITICAL / HIGH security finding | security ≤ 2.0 / ≤ 5.0; verdict at least `REQUEST_CHANGES` |
| credible CRITICAL / HIGH correctness finding | correctness ≤ 3.0 / ≤ 5.0; verdict at least `REQUEST_CHANGES` |
| any active MEDIUM+ finding | verdict at least `COMMENT` |
| BUGFIX or FEATURE without test changes | test coverage ≤ 4.0 |
| truncated diff, skipped reviewer, degraded agent, unsettled debate | confidence capped, limitation added |
| always | clamp, round to 0.1, recompute overall |

Credible means `CONFIRMED`, or `UNVERIFIED` with confidence ≥ 0.7; `UNSURE`, `REJECTED` and dropped findings never
count. `verdict = stricter(proposed, floor)`, and the report names the rule that made it stricter.

---

## 12. Human in the loop

`ReviewerConsole` stays the human channel (`askClarifications`, `askApproval`), held by `ActiveReview`:

| Implementation | Used by | Behavior |
|---|---|---|
| `StdinReviewerConsole` | `review --mode interactive` | Console prompts; grammar `approve`, `edit <notes>`, `contest F3 F7 [notes]`, `drop F2 [notes]`, `reject`; `--approve-on-eof` |
| `WebReviewerConsole` | the web interface | Publishes the pending interaction as an event, blocks the review thread on a future until the browser answers; no timeout |
| `ScriptedReviewerConsole` | tests | Scripted answers |

The approval rules are shared: contested ids must exist; past the cost cap, `contest` is refused and `drop` or `edit`
offered instead; a contest after the last pass is applied without a debate. `REJECTED` writes only the trace.

---

## 13. Outputs

`report.md` (verdict and why, scores, what is good, what is wrong with finding ids and locations, what could be
improved, limitations, models and cost), `report.json` (the `ReviewReport`) and `trace.html`, in
`<output-dir>/<owner>-<repo>-<number>/`. The writer produces the prose only (`ReportDraft`); `reportAssembler` renders
findings, scores and verdict from the data, so every MEDIUM+ finding is listed and none can be invented. The CLI
prints a summary; the web interface renders the report (section 17.4).

---

## 14. Reliability

### 14.1 Root error handler

One `@ErrorHandler` on the root sees every agent's failure (the mapper's `_<n>` suffix is stripped):

| Failure | Recovery |
|---|---|
| missing argument | throw: a wiring bug |
| account problem: invalid key, unknown model, no credits or quota | throw at once: every later call would fail the same way |
| transient (rate limit, overload, connection; not timeouts) | retry once, counted in `attempts.<agent>` |
| reviewer, voter, debater, judge, writer, critic, triage, gatherer, router, … | neutral fallback, recorded in `degradedAgents` and listed in the limitations |
| prFetcher, scorer, or anything without a fallback | throw |

The account-problem test matches langchain4j's exception types. The Quarkus model clients run on Quarkus' REST client,
so Phase 0 checks which exceptions they throw for 401, 404 and "credit balance too low", and extends the test (HTTP
status and message) if they differ.

### 14.2 Limits

`--max-files` 50, `--max-diff-tokens` 60K (3.5 characters per token), generated, vendored, lock and binary files
dropped, `--max-chunks` 4 for the left-out files, tool budgets 12/6/6, all loops bounded (3 passes, 3 report rounds,
`--max-debates` per revision), and the soft cost cap consulted by the voting gate, the debate gate and guard, and the
model suppliers.

### 14.3 Safety

Guard paragraph in every prompt, with the PR bundle in the user message and nothing untrusted in system messages;
read-only tools and token; findings outside the diff dropped; no agent writes to GitHub. The web interface adds its own
rules (section 17.5).

---

## 15. Observability and cost

- `CostTrackingListener` (`ChatModelListener` bean, attached by the extension to every configured model) prices each
  call with `PriceTable` from the response's model name and token usage (cache reads and writes included) and adds it to
  `ActiveReview`'s ledger, attributed to the agent `AgentAttribution` reports for the current thread.
- `DecisionCostListener` (`DecisionModelListener` bean) prices decision calls; local ones cost $0.
- `AgentAttribution` (`AgentListener` bean) tracks the running agent per thread, parallel branches included.
- `ProgressListener` (`AgentListener` bean) publishes agent started, finished and failed events, with duration, model
  and running cost, to `ActiveReview`'s event sink: the CLI prints them, the web interface streams them.
- The ledger exposes `total()`, `exceeded(cap)` and the decisions the cap caused, which the report lists.

quarkus-langchain4j's own cost estimators cover only some OpenAI models and none of Anthropic's, so `PriceTable` stays.
Micrometer metrics are out of scope.

---

## 16. CLI

### 16.1 Commands

| Command | What it does |
|---|---|
| `pr-reviewer review <PR> [options]` | Reviews one pull request; console interaction in interactive mode |
| `pr-reviewer serve [--port 8080]` | Starts the web interface on 127.0.0.1 and waits (`Quarkus.waitForExit()`) |
| `pr-reviewer eval run [--eval FILE] [--runs 3] [--limit N] [review options]` | Scores the reviewer against the eval set (today's `EvalRunner`) |
| `pr-reviewer eval build-set [--repos a/b,c/d] [--per-repo 4] [--max-files 15] [--max-lines 800] [--out FILE]` | Builds the eval set from closed pull requests (today's `EvalSetBuilder`) |
| `pr-reviewer completion` | Prints the bash/zsh completion script |

`review` options, grouped in the help with `@ArgGroup` headings: *Review* (`--mode`, `--deep`, `--out`,
`--approve-on-eof`, `--quiet`), *Models* (`--provider`, `--model-tier`, `--decision-model`, `--router-threshold`),
*Budgets* (`--max-cost`, `--max-debates`, `--max-files`, `--max-diff-tokens`, `--max-chunks`).
Enum values are case-insensitive. Exit codes: 0 when the review completes (report written, or rejected by the human),
1 on failure, 2 on a usage error.

### 16.2 Startup

- `PrReviewerMain` is the `@QuarkusMain`. Unless the first argument is `serve`, it sets `quarkus.profile=cli` before
  `Quarkus.run`: `%cli.quarkus.http.host-enabled=false` (otherwise every command would start the HTTP server and
  collide with a running `serve`), `%cli.quarkus.banner.enabled=false`, `%cli.quarkus.log.level=WARN`. For `serve` it
  maps `--port` to `quarkus.http.port`, also before startup.
- `PrReviewerApp` (`QuarkusApplication`) runs `new CommandLine(TopCommand.class, factory)` with the extension's CDI
  factory, so commands are beans that inject `ReviewService`. It sets case-insensitive enums, the execution exception
  handler (one line with the root cause) and the default-value provider.
- `ConfigDefaultProvider` (`IDefaultValueProvider`) reads `reviewer.<option-name>` from MicroProfile Config, so
  `application.properties`, environment variables (`REVIEWER_MAX_COST`) and `-D` flags set the defaults of the CLI and
  the web form alike, and `--help` shows the effective default.

### 16.3 Output

Progress lines on stderr while the review runs (one per agent start and end: name, model size, duration, running
cost); `--quiet` keeps only the summary. The summary on stdout: verdict, overall and confidence, the metric table,
findings listed as wrong, the cost ledger, and the report directory, with ANSI styles that picocli turns off when the
output is not a terminal.

### 16.4 Completion

`Completion extends AutoComplete.GenerateCompletion` with `@Command(name = "completion")` (the pattern of the Quarkus
CLI). It completes subcommands, options, enum values (`--mode`, `--provider`, `--model-tier`, `--decision-model`)
and paths (`--out`, `--eval`). The pull request argument has no candidates: the script is static. Install:

```sh
pr-reviewer completion > ~/.local/share/bash-completion/completions/pr-reviewer     # bash
echo 'autoload -U +X bashcompinit && bashcompinit; source <(pr-reviewer completion)' >> ~/.zshrc   # zsh
```

---

## 17. Web interface

### 17.1 Behavior

- **Queue.** `POST /reviews` enqueues; one worker runs reviews in order. Statuses: `QUEUED`, `RUNNING`,
  `WAITING_FOR_YOU`, `DONE`, `REJECTED`, `FAILED`, `CANCELLED`. Only queued reviews can be cancelled.
- **Live progress.** The run page subscribes to `GET /reviews/{id}/events` (server-sent events) with htmx's SSE
  extension; the server sends HTML fragments rendered by Qute. A reconnecting page first receives the events so far.
- **Interaction.** In interactive mode the run page shows the clarification form, later the approval form: the report,
  then per finding *contest* and *drop* checkboxes with notes, plus *approve*, *edit* and *reject*. The review waits
  without a timeout; reloading the page restores the pending form. The form builds an `ApprovalDecision` and the
  shared rules of section 12 validate it.
- **History.** Built from the output directory (`report.json`, and `trace.html` when present), so it survives
  restarts; in-memory runs add the queued and running ones.

### 17.2 Endpoints

| Method and path | Purpose |
|---|---|
| `GET /` | Form (PR, mode, provider, decision model, deep; budgets and threshold under "advanced"), queue, recent reviews |
| `POST /reviews` | Enqueue, redirect to the run page |
| `GET /reviews/{id}` | Run page: status, progress timeline, running cost, pending form |
| `GET /reviews/{id}/events` | SSE: `status`, `agent`, `cost`, `question`, `approval`, `done`, `failed` |
| `POST /reviews/{id}/clarifications` | Answers to the questions |
| `POST /reviews/{id}/approval` | The approval decision |
| `POST /reviews/{id}/cancel` | Cancel a queued review |
| `GET /reports/{dir}` | The report rendered as HTML |
| `GET /reports/{dir}/{file}` | `report.md`, `report.json`, `trace.html` only |

### 17.3 Stack

`quarkus-rest`, `quarkus-rest-qute` (templates in `src/main/resources/templates`, fragments for SSE),
`quarkus-rest-jackson`, `quarkus-web-dependency-locator` with `org.mvnpm:htmx.org` and `org.mvnpm:htmx-ext-sse`
(versions pinned in Phase 3), one CSS file with light and dark themes. SSE through `Multi<OutboundSseEvent>` from a
per-run Mutiny broadcast processor.

### 17.4 Report rendering

A header rendered from the data (verdict, overall, confidence, metrics, cost, links) above `report.md` rendered to
HTML by commonmark-java with raw HTML escaped and URLs sanitized. Qute escapes everything else by default.

### 17.5 Safety

PR text reaches the browser, and a local server is reachable from any page the user visits:

- bind to 127.0.0.1 (`quarkus.http.host`);
- a request filter rejects requests whose `Host` is not `localhost`/`127.0.0.1` (DNS rebinding) and cross-origin
  `Origin` headers;
- CSRF tokens on every form (`quarkus-rest-csrf`);
- report HTML escaped (17.4); `trace.html` served with `Content-Security-Policy: sandbox allow-scripts`, so its
  scripts run in an opaque origin; `report.md` as `text/plain`;
- downloads limited to the three file names inside the output directory, `{dir}` validated against
  `owner-repo-number`;
- no keys or tokens in pages or logs.

---

## 18. Project layout, build, distribution

### 18.1 Layout

```
langchain4j-pr-reviewer/
├── pom.xml, PLAN.md, README.md, jbang-catalog.json
├── eval/eval-set.json
├── docs/                      topology.html, images/*.svg
└── src/main/java/dev/langchain4j/prreviewer/
    ├── PrReviewSystem.java    root @SequenceAgent (@BeforeCall defaults, @ErrorHandler)
    ├── RootErrorHandler.java
    ├── app/                   ReviewService, ActiveReview, ReviewRun, ReviewRequest, RunEvent
    ├── cli/                   PrReviewerMain, PrReviewerApp, PrReviewerCommand, ReviewCommand, ServeCommand,
    │                          EvalCommand, Completion, ConfigDefaultProvider, ProgressPrinter, VersionProvider
    ├── web/                   ReviewResource, ReportResource, ReviewQueue, WebRun, Interaction, WebReviewerConsole,
    │                          ReportHistory, MarkdownRenderer, LocalRequestFilter, Pages, Views, Fragments
    ├── config/                ReviewerConfig, ReviewDefaults, ReviewerConfigSource
    ├── keys/                  Keys (String constants and their defaults), ScopeState
    ├── model/                 records and enums (ReviewOptions gains provider and decisionModel)
    ├── models/                ModelCatalog, ModelSelection, DecisionModels, StructuredOutput, StatelessAgents
    ├── github/                GitHubReads, HubGitHubReads, GitHubTools, PullRequestReader,
    │                          DiffParser, DiffPreprocessing, PrUrl
    ├── agents/                AI agent interfaces, ToolBudget
    ├── workflow/              composed agents, non-AI agent beans, projections
    ├── scoring/, hitl/, observability/, eval/
src/main/resources/            application.properties, prompts/, templates/, META-INF/resources/ (CSS)
```

Deleted: `AppContext`, `PrReviewSystems`, `AnthropicModels`, `OpenAiModels`, `EffortHttpClientBuilder`, `ChatModels`,
`ChatModelProvider` (replaced by `ModelProvider`, `DecisionProvider` and `ModelCatalog`), the client-building half of
`GitHubMcp` and `DecisionModels`, `eval/*.sh`.

### 18.2 Dependencies

- BOMs: `io.quarkus.platform:quarkus-bom:3.40.x`, `io.quarkiverse.langchain4j:quarkus-langchain4j-bom:1.15.0.CR1`.
- quarkus-langchain4j: `quarkus-langchain4j-agentic`, `-anthropic`, `-openai`, `-typesafe`.
- GitHub: `io.quarkiverse.githubapi:quarkus-github-api:1.330.0`, which brings hub4j 1.330 (`org.kohsuke:github-api`).
- langchain4j: `langchain4j-agentic-patterns` (from the BOM, 1.21.0-beta31).
- Quarkus: `quarkus-picocli`, `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-rest-qute`, `quarkus-rest-csrf`,
  `quarkus-web-dependency-locator`, `quarkus-arc`.
- Web assets: `org.mvnpm:htmx.org`, `org.mvnpm:htmx-ext-sse`. Rendering: `org.commonmark:commonmark`.
- Tests: `quarkus-junit5`, `rest-assured`, AssertJ.
- Removed: `langchain4j-bom` 1.22.0, `langchain4j-http-client-jdk`, `slf4j-simple`, the explicit Jackson and picocli
  versions, `maven-shade-plugin`.
- Build: `quarkus-maven-plugin`; compiler `-parameters`; surefire and failsafe (`-Pit`) with the JBoss log manager.

### 18.3 Distribution

- `quarkus.package.jar.type=uber-jar`: `target/langchain4j-pr-reviewer-1.0.0-SNAPSHOT-runner.jar`.
- `jbang-catalog.json` at the repository root:

  ```json
  {
    "aliases": {
      "pr-reviewer": {
        "script-ref": "dev.langchain4j:langchain4j-pr-reviewer:1.0.0-SNAPSHOT:runner",
        "description": "Agentic GitHub pull request reviewer",
        "java": "21+"
      }
    }
  }
  ```

  `mvn install`, then `jbang app install pr-reviewer@<path-to-repo>` puts `pr-reviewer` on the PATH (`jbang app
  setup` once). JBang 0.134.2 accepts `group:artifact:version:classifier`; Phase 4 verifies that `mvn install` installs
  the runner jar with the `runner` classifier, and otherwise points `script-ref` at the jar path. Once the project is
  published, the alias moves to the release.

---

## 19. Testing

| Kind | How | Examples |
|---|---|---|
| Pure logic (plain JUnit, unchanged apart from the keys) | no CDI | `DiffParser`, `DiffPreprocessing`, `PrUrl`, `Consolidation`, `Caps`, `VerdictRules`, `VotingTrigger`, `ScoreCardVotingStrategy`, `ApprovalCommandParser`, `ReportRenderer`, `PriceTable`, `RootErrorHandler` classification, `ToolBudget`, `PullRequestReader` on recorded REST responses |
| System (`@QuarkusTest`, one test profile) | test producers override the provider beans: `@Produces @ModelName("anthropic-standard") ChatModel` … backed by `ScriptedChatModel`, which answers per agent (the current agent comes from `AgentAttribution`); a fake `DecisionModel` per name; a `@Mock` `GitHubReads` answering from the scenario's fixtures; `ScriptedReviewerConsole`; placeholder API keys so startup succeeds | today's `PrReviewSystemTest` and `ScriptedModelReviewTest` scenarios: router activations and floor, AUTO skips the human agents, contest runs one debate and re-scores, drop removes a finding from the floor, voting only when borderline, report loop exits, degradation instead of failure, cost cap skips voting, debates and escalation |
| CLI (`@QuarkusMainTest`, `@Launch`) | same producers | `review` with scripted models prints the summary and writes the report; `--help` lists groups and config defaults; `completion` output passes `bash -n`; usage errors exit with 2 |
| Web (`@QuarkusTest` + RestAssured) | same producers | enqueue → SSE events → report; interactive round trip (questions, contest, approve); queue order and cancel; CSRF and Host rejection; path traversal on downloads; a `<script>` in the PR title and body comes out escaped |
| Integration (`-Pit`, real services) | real configuration | `OpenAiReviewIT`, `OpenAiRouterIT`, `ReviewRouterIT`/`HostedReviewRouterIT` (scripted reviewers, real decision model), `AnthropicReviewIT` (needs credits), `GitHubApiIT` (a public pull request, a file, a directory, a code search and the commit list through the real API; asserts the five tool names) |
| Documentation | `@QuarkusTest` | `TopologyDocumentationTest` regenerates `docs/topology.html` with `-Dupdate.topology=true` |

Replacing leaf agents with stubs is not reliable under the extension (the root captures its sub-agents once, when it
is built), so scenarios that used stub agents now script the models instead.

---

## 20. Migration phases

**Prerequisite:** the project is not under version control; `git init` and a baseline commit first, so each phase is a
commit and the spike a branch.

| Phase | Deliverable | Verification |
|---|---|---|
| 0. Spike | Quarkus build with every extension; keys converted; projections and `ReportRound`; root injected; one non-AI agent and one projection as beans; scripted models through test producers. Checks: the validation passes (and the `DiffChunk` mapper item), non-AI beans work as sub-agents, CDI parameters on the model and tool suppliers, `AgentListener` beans notified once per invocation (not twice with `inheritedBySubagents`), native structured output for records on both providers, exception types for 401, 404 and credit errors, MCP command escaping and lazy start, startup without any key, the uber-jar runs, the first agent call on a virtual thread (quarkus-langchain4j issue #2593) | `mvn verify` green with one scripted end-to-end `@QuarkusTest`; a written list of check results, with the fallback chosen for any failed check |
| 1. Core port | All agents converted; `ActiveReview`, `ReviewService`; `ModelCatalog`, `ModelSelection`, `DecisionModels`, `GitHubMcp`, `BudgetedToolProvider`; `ReviewerConfigSource`; listeners as beans; old builders deleted; tests migrated | All unit and system tests green; `OpenAiReviewIT` and the router ITs green; cost of the OpenAI IT compared with the last run (COMMENT 8.2/10, $0.50) |
| 2. CLI | `PrReviewerMain`, `PrReviewerApp`, commands, `ConfigDefaultProvider`, progress and summary output, completion, eval subcommands | CLI tests green; manual tab completion in bash and zsh; `review` while `serve` runs does not touch port 8080 |
| 3. Web | Endpoints, templates, SSE, `WebReviewerConsole`, history, safety filters, CSS | Web tests green; manual run in a browser (light and dark): an AUTO review and an INTERACTIVE one with a contest |
| 4. Distribution and docs | Uber-jar, `jbang-catalog.json`; README (quick start, CLI, web, completion, configuration); `docs/topology.html` and the README topology images updated with the new nodes; this plan's notes updated | `jbang app install` → `pr-reviewer --version` and `pr-reviewer review` on a public PR; completion works from the installed command; every README command executed once |

---

## 21. Risks, verification points, parked and dropped items

### 21.1 Risks

| Risk | Mitigation |
|---|---|
| The validation rejects more than the inventory of 5.3 | Phase 0 runs the full build first; each new case gets a projection or an `AgenticScope` read |
| Non-AI beans do not work as sub-agents | Keep them static with `Arc` lookups (6.3) |
| Quarkus clients throw other exception types for account errors | Extend the account-problem test (14.1) in Phase 0 |
| Structured output not native through the Quarkus config | Check in Phase 0; set the response format per named model, or pass a JSON schema capability through a `ModelBuilderCustomizer` |
| Listener notified twice per agent | Drop `inheritedBySubagents` or deduplicate by invocation |
| Request context or class loader issues on parallel virtual threads (#2593, fix in PR #2946) | Phase 0 check; set the context class loader in a `@ParallelExecutor`, or wait for 1.15.0 final |
| Cost goes up on Anthropic without per-tier effort and automatic caching | Measure with `AnthropicReviewIT` once the account has credits; lower `max-tokens` on the small model if needed |
| 1.15.0.CR1 is a release candidate | Move to 1.15.0 final in Phase 4 at the latest |
| A browser left on a pending interaction blocks the queue | Visible as `WAITING_FOR_YOU` on the home page; single user by design |

### 21.2 Parked until quarkus-langchain4j moves to langchain4j 1.22

- Anthropic automatic prompt caching (`cacheAutomatically`, `cacheTtl`): back through a `ModelBuilderCustomizer`.
- The OpenAI decision model (`--decision-model openai`, `OpenAiDecisionModel`): back as a hand-built `DecisionModel`
  bean, since the extension has no producer for it.

### 21.3 Dropped

- Anthropic per-tier effort and `EffortHttpClientBuilder`.
- Stub sub-agent tests (replaced by scripted models).
- The `--pr` option and the `eval/*.sh` scripts.

---

### 21.4 Implementation notes (2026-10-15)

Phases 0 to 4 are done on the `quarkus-port` branch, one commit each; 95 unit, orchestration, CLI and web tests pass,
and the integration tests pass against the real services (GitHub MCP in Docker, local and hosted decision models,
OpenAI, account errors).

| Area | Plan | Implementation | Why |
|---|---|---|---|
| Validation | 7 projections | 9 new nodes: also `debateContext` (written by the debate planner) and the `diffChunk` declaration (mapper item) | The build reported both; the static check that replicates the validator listed them at once |
| Non-AI agents | `@ApplicationScoped` beans | Static methods with `Arc` lookups | Spike: an instance `@Agent` method makes langchain4j build an AI service (6.3) |
| Agent memory | Not planned | `StatelessAgents`: a `ChatMemoryProvider` whose memories keep nothing | Spike: Quarkus registers agents as AI services with the default memory provider; agents off the request context share memory id `default`, so parallel reviewers read the usefulness assessor's conversation. Quarkus still buffers one invocation's messages, so tool loops work |
| Structured output | Response format per named model | `ModelBuilderCustomizer` per named model (7.1) | The config offers the capability only with a JSON default format |
| Missing keys | ConfigSource toggling `enable-integration` | Placeholder keys plus availability checks (7.3) | Same effect, no config generation |
| Tools | `GitHubTools` bean | `GitHubMcp.readTools(budget)` | One bean to replace in tests |
| `null` scope writes | Unchanged | `ScopeState.reset` writes the key's default | With String keys a `null` value is a missing argument for the next reader |
| Tests | Scripted models | Scripted models answer by agent (from `AgentAttribution`), then by prompt task | Fallback reviewers share the prompt of the main ones |
| Integration tests | Real services | Test profiles exclude the test doubles with `quarkus.arc.exclude-types`; a test class extending a concrete `@QuarkusTest` is ambiguous, hence `AbstractReviewRouterIT` | Quarkus makes test classes beans |
| CLI | As planned | Logs on stderr in the `cli` profile | Quarkus logged to stdout, which corrupted the completion script |
| JBang | GAV with classifier | Works; the alias also passes the manifest's `Add-Opens` as `java-options` | JBang starts the jar from its class path, without the manifest's options |
| Version | 1.15.0 final in Phase 4 | Still 1.15.0.CR1 | 1.15.0 is not released yet |

Measured: `OpenAiReviewIT` on langchain4j PR 6600 gave `COMMENT` 8.2/10 for $0.28 (before the port: `COMMENT` 8.2/10,
$0.50; the difference is the length of the large model's correctness review). Account errors from the Quarkus model
clients (invalid key, unknown model, on both providers) are recognized by the error handler unchanged.

### 21.5 GitHub through quarkus-github-api (2026-10-20)

Planned in `GITHUB-API-PLAN.md` after the review of the quarkus.io article asked why MCP rather than the extension;
implemented on the `github-api` branch. What the implementation found that the plan did not know:

| Area | Plan | Implementation | Why |
|---|---|---|---|
| The extension | Throttled pagination helper and rate-limit retry configuration | Only the hub4j dependency and native-image substitutions; `PagedIterable.withPageSize` and hub4j's own handlers | At 1.330.0 (and 2.0.0.CR7) the runtime jar holds two GraalVM substitutions and nothing else |
| Tool executors | Quarkus build-time executors, to be confirmed by a spike | Confirmed: the agentic extension passes the bean to `AgentBuilder.tools(...)`, and the Quarkus `AiServices` resolves it against the build-time tool metadata; the wrapper sees a non-null `invocationId` and the agent interface name | `GitHubToolsWiringTest` drives twelve tool calls and the refused thirteenth through a scripted model |
| Tool loop | Not considered | `quarkus.langchain4j.ai-service.max-tool-executions=16` | The agents read this (deprecated) property; its default of 10 round trips fails the invocation before a budget of 12 is reached, which was already true with the MCP tools |
| Failures in a tool call (Q10) | Propagate: the agent fails and the error handler substitutes its fallback | GitHub errors reach the model as text; argument type errors fail the invocation | The agentic path builds its executors with exception propagation off, with no hook to change it; the first behaviour is what drawback 4 of the plan preferred anyway |
| Timeouts | Retried once | Not retried, like model timeouts | The error handler's existing rule: an attempt can run for the whole timeout |
| `closedPullRequests` | Paged by number | A lazy `Stream` of summaries; details fetched per candidate | Avoids one request per listed pull request |
| Fixtures | REST shapes under `src/test/resources/github` | As planned, re-recorded with `gh api` for the commits and review comments of 6610 and 6584, plus the whole of 6584 | The MCP shapes of commits and threads differed from REST |

---

## 22. Design notes carried over from revision 4

Departures from the first design, found while building it; they still hold after the port.

| Area | Choice | Why |
|---|---|---|
| Coverage floor | Correctness, security and docs reviews forced when the router skips them on the matching changes | Local `tev1:0.8b`: 22/25 decisions at its best threshold, often selects nothing at 0.5 |
| Router input | Explicit flags; descriptions state the flags they need | Topic descriptions: 13/25 locally; flags: 22/25 local, 25/25 hosted |
| Router threshold | 0.2 local, 0.5 hosted, overridable | `tev1:0.8b`: 17/25 at 0.5, 22/25 around 0.2 |
| Thinking | Returned and sent back | Thinking blocks must come back unchanged in tool loops |
| Report | Writer returns prose (`ReportDraft`); `reportAssembler` renders findings, scores and verdict | No finding invented or lost; the critic checks the rendered report |
| Model-facing records | `RawFinding`, `ScoreCard` with `MetricScore`, `DebateOutcome`, `ReportDraft` | No nullable or map fields in JSON schemas; ids, verification and calibration stay in code |
| Prompt layout | PR bundle in the user message; system message holds the role and the guard | Untrusted content stays out of system prompts |
| Output budget | 16K small; 32K (Anthropic) or 64K (OpenAI) for the others; 10-minute timeout | Thinking shares the budget; a gpt-5.1 correctness review used 38K tokens |
| Unsettled findings | `UNSURE` never drives caps or the floor | Otherwise a contest after the last round changed nothing |
| Debaters' input | Only the changes of the finding's file | Four debater turns each resent up to 60K tokens |
| Retries | Timeouts not retried by the agent handler | Each retry may last the full timeout and bypass the cost cap |
| Mapper failures | Copies named `<agent>_<index>`; the handler strips the suffix | One malformed chunk answer used to fail the whole chunked review |
| Security heuristic | Component names with word guards, dangerous calls, secrets, dependency changes | `token`, `auth`, `cert` matched `TokenUsage`, `Author`, `uncertain` |
| Account errors | Stop the run at the first agent | A "credit balance too low" refusal used to degrade four agents before the scorer stopped |
| Providers | Anthropic and OpenAI behind the same tiers | The tier abstraction needed no change |
| OpenAI tool models | `reasoning_effort: none` where supported | gpt-5.6-luna rejects function tools with a reasoning effort |
| Token estimate | 3.5 characters per token | Offline and free; only used for budgets |

Measured on 2026-10-14 with OpenAI models: COMMENT 8.2/10 on the test PR for $0.50, $0.39 of it the large-model
correctness escalation. Anthropic runs are unmeasured (no credits on the account).

---

## 23. Extensions

- An Anthropic-backed `DecisionModel` adapter as a fallback when TypeSafe is unavailable.
- Suspended human-in-the-loop with a persisted `AgenticScope`, so a pending review survives a server restart.
- Re-review on new commits: diff against the previously reviewed head sha, carrying verified and dropped findings.
- A repository conventions file injected into the design reviewer's prompt.
- Micrometer metrics (`gen_ai.client.token.usage`, cost) through the extension, once `PriceTable` is exposed as a
  `CostEstimator`.
- Posting the report as a PR comment, behind an explicit flag and the approval gate, if the read-only rule is relaxed.
