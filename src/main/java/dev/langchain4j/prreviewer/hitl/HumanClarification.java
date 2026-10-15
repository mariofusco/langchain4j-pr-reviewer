package dev.langchain4j.prreviewer.hitl;

import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.prreviewer.app.ActiveReview;
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
    static String ask(PullRequest pullRequest, PrSummary prSummary, List<String> clarificationQuestions) {
        return ActiveReview.get().console().askClarifications(pullRequest, prSummary, clarificationQuestions);
    }
}
