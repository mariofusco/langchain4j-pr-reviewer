package dev.langchain4j.prreviewer.cli;

import java.util.Set;
import org.eclipse.microprofile.config.ConfigProvider;
import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.OptionSpec;

/**
 * Defaults of the review options from MicroProfile Config ({@code reviewer.<option>}): {@code application.properties},
 * environment variables such as {@code REVIEWER_MAX_COST}, or {@code -D} flags. The web form reads the same keys, so
 * both front ends share one set of defaults, and {@code --help} shows the effective one.
 */
public class ConfigDefaultProvider implements IDefaultValueProvider {

    static final Set<String> REVIEW_OPTIONS = Set.of("provider", "decision-model", "router-threshold", "mode", "deep",
            "model-tier", "max-cost", "max-debates", "max-files", "max-diff-tokens", "max-chunks");

    @Override
    public String defaultValue(ArgSpec arg) {
        if (!(arg instanceof OptionSpec option)) {
            return null;
        }
        String name = option.longestName().replaceFirst("^--", "");
        String command = option.command().name();
        String key;
        if (command.equals("review") && name.equals("out")) {
            key = "reviewer.output-dir";
        } else if ((command.equals("review") || command.equals("run")) && REVIEW_OPTIONS.contains(name)) {
            key = "reviewer." + name;
        } else {
            return null;
        }
        return ConfigProvider.getConfig().getOptionalValue(key, String.class).orElse(null);
    }
}
