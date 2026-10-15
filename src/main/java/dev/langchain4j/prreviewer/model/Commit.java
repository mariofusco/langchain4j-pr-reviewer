package dev.langchain4j.prreviewer.model;

public record Commit(String sha, String message, String author) {

    /** The first line of the commit message. */
    public String headline() {
        int newline = message.indexOf('\n');
        return (newline < 0 ? message : message.substring(0, newline)).strip();
    }
}
