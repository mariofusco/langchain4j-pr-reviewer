package dev.langchain4j.prreviewer.cli;

import dev.langchain4j.prreviewer.eval.EvalRunner;
import dev.langchain4j.prreviewer.eval.EvalSetBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "eval", mixinStandardHelpOptions = true,
        description = "Builds the eval set from closed pull requests, and scores the reviewer against it.",
        subcommands = {EvalRunner.class, EvalSetBuilder.class})
public class EvalCommand implements Runnable {

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
