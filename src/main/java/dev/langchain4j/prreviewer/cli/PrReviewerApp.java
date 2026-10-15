package dev.langchain4j.prreviewer.cli;

import io.quarkus.runtime.QuarkusApplication;
import jakarta.inject.Inject;
import picocli.CommandLine;

/** Runs the picocli command line with commands created by CDI. */
public class PrReviewerApp implements QuarkusApplication {

    @Inject
    CommandLine.IFactory factory;

    @Override
    public int run(String... args) {
        return commandLine(factory).execute(args);
    }

    static CommandLine commandLine(CommandLine.IFactory factory) {
        CommandLine cli = new CommandLine(PrReviewerCommand.class, factory)
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setExecutionExceptionHandler((error, cmd, parseResult) -> {
                    cmd.getErr().println(cmd.getColorScheme().errorText("Error: " + rootMessage(error)));
                    return 1;
                });
        cli.setDefaultValueProvider(new ConfigDefaultProvider());
        return cli;
    }

    static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return root == error ? message : error.getMessage() + " (caused by " + message + ")";
    }
}
