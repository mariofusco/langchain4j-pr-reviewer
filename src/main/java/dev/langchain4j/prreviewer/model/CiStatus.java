package dev.langchain4j.prreviewer.model;

/** Counts of the check runs on the head commit. */
public record CiStatus(int total, int succeeded, int failed, int pending) {

    public static CiStatus unknown() {
        return new CiStatus(0, 0, 0, 0);
    }

    public String summary() {
        if (total == 0) {
            return "no checks reported";
        }
        return total + " checks: " + succeeded + " passed, " + failed + " failed, " + pending + " pending or other";
    }
}
