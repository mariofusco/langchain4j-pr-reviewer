package dev.langchain4j.prreviewer.cli;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Entry point. Every command but {@code serve} runs with the {@code cli} profile: no HTTP server (it would collide
 * with a running {@code serve}), no banner, warnings only. {@code serve --port} is applied before Quarkus starts.
 */
@QuarkusMain
public class PrReviewerMain {

    public static void main(String... args) {
        boolean serve = args.length > 0 && args[0].equals("serve");
        if (serve) {
            for (int i = 1; i < args.length; i++) {
                if (args[i].equals("--port") && i + 1 < args.length) {
                    System.setProperty("quarkus.http.port", args[i + 1]);
                } else if (args[i].startsWith("--port=")) {
                    System.setProperty("quarkus.http.port", args[i].substring("--port=".length()));
                }
            }
        } else if (System.getProperty("quarkus.profile") == null && System.getenv("QUARKUS_PROFILE") == null) {
            System.setProperty("quarkus.profile", "cli");
        }
        Quarkus.run(PrReviewerApp.class, args);
    }
}
