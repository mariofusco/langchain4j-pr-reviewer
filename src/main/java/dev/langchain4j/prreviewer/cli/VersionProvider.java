package dev.langchain4j.prreviewer.cli;

import org.eclipse.microprofile.config.ConfigProvider;
import picocli.CommandLine.IVersionProvider;

/** The application version from the build. */
public class VersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() {
        return new String[] {"pr-reviewer " + ConfigProvider.getConfig()
                .getOptionalValue("quarkus.application.version", String.class).orElse("unknown")};
    }
}
