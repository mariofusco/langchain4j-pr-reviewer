package dev.langchain4j.prreviewer.support;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.agents.ChunkReviewer;
import dev.langchain4j.prreviewer.agents.ContextGatherer;
import dev.langchain4j.prreviewer.agents.CorrectnessReviewer;
import dev.langchain4j.prreviewer.agents.CorrectnessReviewerFallback;
import dev.langchain4j.prreviewer.agents.DebateJudge;
import dev.langchain4j.prreviewer.agents.DesignReviewer;
import dev.langchain4j.prreviewer.agents.DocsApiReviewer;
import dev.langchain4j.prreviewer.agents.DocsApiReviewerFallback;
import dev.langchain4j.prreviewer.agents.SecurityReviewerFallback;
import dev.langchain4j.prreviewer.agents.FindingDefender;
import dev.langchain4j.prreviewer.agents.FindingProsecutor;
import dev.langchain4j.prreviewer.agents.LenientScorer;
import dev.langchain4j.prreviewer.agents.NeutralScorer;
import dev.langchain4j.prreviewer.agents.PrTriage;
import dev.langchain4j.prreviewer.agents.QuestionGenerator;
import dev.langchain4j.prreviewer.agents.ReportCritic;
import dev.langchain4j.prreviewer.agents.ReportFinalizer;
import dev.langchain4j.prreviewer.agents.ReportWriter;
import dev.langchain4j.prreviewer.agents.Scorer;
import dev.langchain4j.prreviewer.agents.SecurityReviewer;
import dev.langchain4j.prreviewer.agents.SkepticalScorer;
import dev.langchain4j.prreviewer.agents.TestReviewer;
import dev.langchain4j.prreviewer.agents.UsefulnessAssessor;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Category;
import dev.langchain4j.prreviewer.model.Critique;
import dev.langchain4j.prreviewer.model.DebateOutcome;
import dev.langchain4j.prreviewer.model.DebateVerdict;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.FindingCategory;
import dev.langchain4j.prreviewer.model.Metric;
import dev.langchain4j.prreviewer.model.MetricScore;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.RawFinding;
import dev.langchain4j.prreviewer.model.ReportDraft;
import dev.langchain4j.prreviewer.model.Risk;
import dev.langchain4j.prreviewer.model.ScoreCard;
import dev.langchain4j.prreviewer.model.Severity;
import dev.langchain4j.prreviewer.model.Size;
import dev.langchain4j.prreviewer.model.UsefulnessAssessment;
import dev.langchain4j.prreviewer.model.Verdict;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Non-AI stand-ins for every AI agent, with the same names and output keys, for orchestration tests without models.
 * Every stub counts its invocations; outputs can be replaced per test.
 */
public final class StubAgents {

    public static final String MAIN_FILE = "langchain4j/src/main/java/dev/langchain4j/service/DefaultAiServices.java";
    public static final String VALIDATION_FILE = "langchain4j/src/main/java/dev/langchain4j/service/AiServiceValidation.java";

    private record Stub(String name, Class<? extends TypedKey<?>> typedKey, String key, Type type, boolean async,
                        Function<AgenticScope, Object> body) {}

    private final Map<Class<?>, Stub> stubs = new HashMap<>();
    private final Map<String, AtomicInteger> invocations = new ConcurrentHashMap<>();

    public static StubAgents defaults() {
        StubChunkReviewer.INVOCATIONS.set(0);
        StubChunkReviewer.failIfContains = null;
        StubAgents s = new StubAgents();
        s.stub(PrTriage.class, "prTriage", Keys.PrSummary.class, PrSummary.class,
                scope -> summary(Category.REFACTOR, Risk.MEDIUM, false));
        s.stub(ContextGatherer.class, "contextGatherer", Keys.CodeContext.class, String.class,
                scope -> "DefaultAiServices.build() is called by AiServices.build().");
        s.stub(QuestionGenerator.class, "questionGenerator", Keys.ClarificationQuestions.class, List.class,
                scope -> List.of("Should default methods ever call the model?"));
        s.stubAsync(UsefulnessAssessor.class, "usefulnessAssessor", Keys.Usefulness.class, UsefulnessAssessment.class,
                scope -> new UsefulnessAssessment(8.0, "Removes duplicated decisions.", false, List.of()));
        s.stub(CorrectnessReviewer.class, "correctnessReviewer", Keys.CorrectnessFindings.class, List.class,
                scope -> List.of(raw(FindingCategory.CORRECTNESS, Severity.MEDIUM, MAIN_FILE, 130, 0.9)));
        s.stub(SecurityReviewer.class, "securityReviewer", Keys.SecurityFindings.class, List.class, scope -> List.of());
        s.stub(DesignReviewer.class, "designReviewer", Keys.DesignFindings.class, List.class,
                scope -> List.of(raw(FindingCategory.DESIGN, Severity.LOW, VALIDATION_FILE, 60, 0.7)));
        s.stub(TestReviewer.class, "testReviewer", Keys.TestFindings.class, List.class, scope -> List.of());
        s.stub(DocsApiReviewer.class, "docsApiReviewer", Keys.DocsFindings.class, List.class, scope -> List.of());
        s.stub(CorrectnessReviewerFallback.class, "correctnessReviewerFallback", Keys.CorrectnessFindings.class,
                List.class, scope -> List.of(raw(FindingCategory.CORRECTNESS, Severity.LOW, MAIN_FILE, 125, 0.6)));
        s.stub(SecurityReviewerFallback.class, "securityReviewerFallback", Keys.SecurityFindings.class, List.class,
                scope -> List.of());
        s.stub(DocsApiReviewerFallback.class, "docsApiReviewerFallback", Keys.DocsFindings.class, List.class,
                scope -> List.of());
        s.stub(FindingProsecutor.class, "findingProsecutor", Keys.Prosecution.class, String.class,
                scope -> "The null path is reachable. CONFIRMED");
        s.stub(FindingDefender.class, "findingDefender", Keys.Defense.class, String.class,
                scope -> "A guard upstream prevents it. REJECTED");
        s.stub(DebateJudge.class, "debateJudge", Keys.DebateVerdict.class, DebateVerdict.class,
                scope -> new DebateVerdict(DebateOutcome.CONFIRMED, null, "The prosecutor showed the path."));
        s.stub(Scorer.class, "scorer", Keys.ScoreCard.class, ScoreCard.class,
                scope -> card(8.5, Verdict.APPROVE));
        s.stub(NeutralScorer.class, "neutralScorer", Keys.ScoreVote1.class, ScoreCard.class,
                scope -> card(7.0, Verdict.COMMENT));
        s.stub(SkepticalScorer.class, "skepticalScorer", Keys.ScoreVote2.class, ScoreCard.class,
                scope -> card(6.0, Verdict.COMMENT));
        s.stub(LenientScorer.class, "lenientScorer", Keys.ScoreVote3.class, ScoreCard.class,
                scope -> card(8.0, Verdict.APPROVE));
        s.stub(ReportWriter.class, "reportWriter", Keys.ReportDraft.class, ReportDraft.class, scope -> {
            List<Finding> active = scope.readState(Keys.ActiveFindings.class);
            return new ReportDraft("A focused refactoring.", List.of("Single place for the decision."),
                    active.stream().filter(f -> f.severity().atLeast(Severity.MEDIUM)).map(Finding::id).toList(),
                    List.of("Add a changelog entry."), List.of());
        });
        s.stub(ReportCritic.class, "reportCritic", Keys.ReportCritique.class, Critique.class,
                scope -> new Critique(0.9, List.of()));
        s.stub(ReportFinalizer.class, "reportFinalizer", Keys.ReportDraft.class, ReportDraft.class, scope -> {
            ReportDraft draft = scope.readState(Keys.ReportDraft.class);
            return new ReportDraft(draft.summary() + " (edited by the reviewer)", draft.good(),
                    draft.wrongFindingIds(), draft.improvements(), draft.limitations());
        });
        return s;
    }

    public StubAgents stub(Class<?> agent, String name, Class<? extends TypedKey<?>> key, Type type,
                           Function<AgenticScope, Object> body) {
        return stub(agent, name, key, null, type, false, body);
    }

    public StubAgents stubAsync(Class<?> agent, String name, Class<? extends TypedKey<?>> key, Type type,
                                Function<AgenticScope, Object> body) {
        return stub(agent, name, key, null, type, true, body);
    }

    private StubAgents stub(Class<?> agent, String name, Class<? extends TypedKey<?>> typedKey, String key, Type type,
                            boolean async, Function<AgenticScope, Object> body) {
        stubs.put(agent, new Stub(name, typedKey, key, type, async, body));
        return this;
    }

    /** Replaces the output of an already stubbed agent. */
    public StubAgents returning(Class<?> agent, Function<AgenticScope, Object> body) {
        Stub s = stubs.get(agent);
        stubs.put(agent, new Stub(s.name(), s.typedKey(), s.key(), s.type(), s.async(), body));
        return this;
    }

    /** Makes the chunk reviewer fail on the chunk containing the given text. */
    public StubAgents failingChunkContaining(String text) {
        StubChunkReviewer.failIfContains = text;
        return this;
    }

    /** Makes the agent fail. */
    public StubAgents failing(Class<?> agent) {
        return returning(agent, scope -> {
            throw new IllegalStateException(stubs.get(agent).name() + " failed");
        });
    }

    public Function<Class<?>, Object> resolver() {
        return type -> {
            if (type == ChunkReviewer.class) {
                return StubChunkReviewer.class;
            }
            Stub s = stubs.get(type);
            if (s == null) {
                return null;
            }
            var builder = AgenticServices.nonAiAgentBuilder(scope -> {
                        invocations.computeIfAbsent(s.name(), n -> new AtomicInteger()).incrementAndGet();
                        return s.body().apply(scope);
                    })
                    .name(s.name())
                    .description(description(type))
                    .outputType(s.type())
                    .async(s.async());
            if (s.typedKey() != null) {
                builder.outputKey(s.typedKey());
            } else {
                builder.outputKey(s.key());
            }
            return builder.build();
        };
    }

    /** The real description: the reviewer router routes on descriptions, so stubs keep them. */
    private static String description(Class<?> type) {
        for (java.lang.reflect.Method method : type.getMethods()) {
            dev.langchain4j.agentic.Agent agent = method.getAnnotation(dev.langchain4j.agentic.Agent.class);
            if (agent != null) {
                return agent.description().isBlank() ? agent.value() : agent.description();
            }
        }
        throw new IllegalArgumentException("Not an agent: " + type);
    }

    public int invocations(String agent) {
        if (agent.equals("chunkReviewer")) {
            return StubChunkReviewer.INVOCATIONS.get();
        }
        AtomicInteger count = invocations.get(agent);
        return count == null ? 0 : count.get();
    }

    public List<String> invokedAgents() {
        List<String> names = new ArrayList<>(invocations.keySet());
        names.sort(String::compareTo);
        return names;
    }

    public static PrSummary summary(Category category, Risk risk, boolean needsContext) {
        return new PrSummary("Decide in one place which methods call the model", category, Size.MEDIUM, risk,
                List.of("langchain4j AI services"), needsContext, List.of());
    }

    public static RawFinding raw(FindingCategory category, Severity severity, String file, int line, double confidence) {
        return new RawFinding(category, severity, file, line, category + " issue at " + line,
                "Explanation of the " + category + " issue.", "Fix it.", "some code", confidence);
    }

    public static ScoreCard card(double score, Verdict verdict) {
        List<MetricScore> metrics = new ArrayList<>();
        for (Metric metric : Metric.values()) {
            metrics.add(new MetricScore(metric, true, score, metric.label() + " rationale"));
        }
        return new ScoreCard(metrics, verdict, "Proposed " + verdict + ".", 0.9);
    }
}
