package dev.langchain4j.prreviewer.hitl;

import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.prreviewer.AppContext;
import dev.langchain4j.prreviewer.keys.Keys;
import dev.langchain4j.prreviewer.model.PrSummary;
import dev.langchain4j.prreviewer.model.PullRequest;
import java.util.List;

/** Human in the loop: asks the generated clarification questions at the console. */
public interface HumanClarification {

    // the declarative human-in-the-loop reads outputKey only, not typedOutputKey: use the key's name
    @HumanInTheLoop(name = "humanClarification",
            description = "Asks the human reviewer the clarification questions and collects guidance for the reviewers",
            outputKey = Keys.REVIEWER_GUIDANCE)
    static String ask(@K(Keys.PullRequest.class) PullRequest pr,
                      @K(Keys.PrSummary.class) PrSummary summary,
                      @K(Keys.ClarificationQuestions.class) List<String> questions) {
        return AppContext.get().console().askClarifications(pr, summary, questions);
    }
}
