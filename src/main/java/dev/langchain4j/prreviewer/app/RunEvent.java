package dev.langchain4j.prreviewer.app;

/**
 * Something that happened during a review, shown live by the CLI and the web interface.
 *
 * @param kind    what happened
 * @param agent   the agent concerned, or null
 * @param model   the model the agent used, or null
 * @param millis  duration of a finished or failed agent, else 0
 * @param costUsd cost of the review so far
 * @param detail  free text: the error of a failed agent, a status
 */
public record RunEvent(Kind kind, String agent, String model, long millis, double costUsd, String detail) {

    public enum Kind {
        AGENT_STARTED,
        AGENT_FINISHED,
        AGENT_FAILED
    }
}
