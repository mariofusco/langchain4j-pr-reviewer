package dev.langchain4j.prreviewer.model;

/** Coordinates of a pull request. */
public record PrRef(String owner, String repo, int number) {

    @Override
    public String toString() {
        return owner + "/" + repo + "#" + number;
    }
}
