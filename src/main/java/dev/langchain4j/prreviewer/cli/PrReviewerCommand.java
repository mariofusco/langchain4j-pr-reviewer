package dev.langchain4j.prreviewer.cli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@TopCommand
@Command(name = "pr-reviewer", mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
        description = "Reviews GitHub pull requests with agents built on langchain4j-agentic. Never writes to GitHub.",
        subcommands = {ReviewCommand.class, ServeCommand.class, EvalCommand.class, Completion.class})
public class PrReviewerCommand implements Runnable {

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
