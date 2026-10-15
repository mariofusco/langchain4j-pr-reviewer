package dev.langchain4j.prreviewer.workflow;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.Finding;
import java.util.List;

/** Non-AI agent: the findings that still count, neither rejected by a debate nor dropped by the reviewer. */
public class ActiveFindingsSelector {

    @Agent(name = "activeFindingsSelector", description = "Selects the findings that still count",
            typedOutputKey = Keys.ActiveFindings.class)
    public static List<Finding> select(@K(Keys.Findings.class) List<Finding> findings) {
        return findings.stream().filter(Finding::active).toList();
    }
}
