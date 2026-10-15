package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.patterns.debate.DebatePlanner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.github.DiffPreprocessing;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.keys.ScopeState;
import dev.langchain4j.prreviewer.model.FileChange;
import dev.langchain4j.prreviewer.model.Finding;
import java.util.List;

/** Non-AI agent: takes the next contested finding off the queue and clears the previous debate. */
public class NextContested {

    @Agent(name = "nextContested", description = "Takes the next contested finding off the queue",
            outputKey = Keys.CONTESTED_FINDING)
    public static Finding next(AgenticScope scope, List<Finding> findings) {
        List<Finding> queue = ScopeState.read(scope, Keys.CONTESTED_FINDINGS);
        List<FileChange> changes = ScopeState.read(scope, Keys.CHANGES);
        Finding head = queue.get(0);
        // the debaters only need the changes of the file concerned, not the whole diff
        String diff = changes.stream()
                .filter(c -> c.path().equals(head.file()))
                .map(DiffPreprocessing::render)
                .findFirst()
                .orElse(ScopeState.read(scope, Keys.DIFF_TEXT));
        scope.writeState(Keys.CONTESTED_DIFF, diff);
        scope.writeState(Keys.CONTESTED_FINDINGS, List.copyOf(queue.subList(1, queue.size())));
        ScopeState.reset(scope, Keys.PROSECUTION);
        ScopeState.reset(scope, Keys.DEFENSE);
        ScopeState.reset(scope, Keys.DEBATE_VERDICT);
        scope.writeState(DebatePlanner.DEBATE_CONTEXT_KEY, "");
        // the queued copy may be stale: debate the current version of the finding
        return findings.stream().filter(f -> f.id().equals(head.id())).findFirst().orElse(head);
    }
}
