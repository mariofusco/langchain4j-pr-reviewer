package dev.langchain4j.prreviewer.web;

/** HTML fragments sent over server-sent events, rendered outside a request (they hold no form). */
final class Fragments {

    private Fragments() {}

    static String progressRow(WebRun.ProgressRow row) {
        // one line: server-sent events split data on line breaks
        return Pages.progressRow(row).render().replace('\n', ' ');
    }
}
