package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.patterns.debate.DebatePlanner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.prreviewer.github.DiffPreprocessing;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.FileChange;
import dev.langchain4j.prreviewer.model.Finding;
import java.util.List;

/** Non-AI agent: takes the next contested finding off the queue and clears the previous debate. */
public class NextContested {

    @Agent(name = "nextContested", description = "Takes the next contested finding off the queue",
            typedOutputKey = Keys.ContestedFinding.class)
    public static Finding next(AgenticScope scope,
                               @K(Keys.ContestedFindings.class) List<Finding> queue,
                               @K(Keys.Findings.class) List<Finding> findings,
                               @K(Keys.Changes.class) List<FileChange> changes) {
        Finding head = queue.get(0);
        // the debaters only need the changes of the file concerned, not the whole diff
        String diff = changes.stream()
                .filter(c -> c.path().equals(head.file()))
                .map(DiffPreprocessing::render)
                .findFirst()
                .orElse(scope.readState(Keys.DiffText.class));
        scope.writeState(Keys.ContestedDiff.class, diff);
        scope.writeState(Keys.ContestedFindings.class, List.copyOf(queue.subList(1, queue.size())));
        scope.writeState(Keys.Prosecution.class, null);
        scope.writeState(Keys.Defense.class, null);
        scope.writeState(Keys.DebateVerdict.class, null);
        scope.writeState(DebatePlanner.DEBATE_CONTEXT_KEY, "");
        // the queued copy may be stale: debate the current version of the finding
        return findings.stream().filter(f -> f.id().equals(head.id())).findFirst().orElse(head);
    }
}
