package dev.langchain4j.prreviewer.cli;

import picocli.AutoComplete;
import picocli.CommandLine.Command;

@Command(name = "completion", hidden = false, mixinStandardHelpOptions = true,
        description = {
                "Prints the bash/zsh completion script of pr-reviewer.",
                "bash: pr-reviewer completion > ~/.local/share/bash-completion/completions/pr-reviewer",
                "zsh:  echo 'autoload -U +X bashcompinit && bashcompinit; source <(pr-reviewer completion)' >> ~/.zshrc"})
public class Completion extends AutoComplete.GenerateCompletion {}
