package dev.langchain4j.prreviewer.cli;

import dev.langchain4j.prreviewer.github.GitHubMcp;
import io.quarkus.runtime.Quarkus;
import jakarta.inject.Inject;
import java.util.concurrent.Callable;
import org.eclipse.microprofile.config.ConfigProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "serve", mixinStandardHelpOptions = true,
        description = "Starts the web interface on this machine (127.0.0.1) and waits; reviews run one at a time.")
public class ServeCommand implements Callable<Integer> {

    // applied by PrReviewerMain before Quarkus starts; declared here for the help and the completion
    @Option(names = "--port", description = "HTTP port (default: 8080)")
    Integer port;

    @Option(names = "--mcp", description = "GitHub MCP server: LOCAL (Docker) or REMOTE (default: ${DEFAULT-VALUE})")
    GitHubMcp.Transport mcp = GitHubMcp.Transport.LOCAL;

    @Spec
    CommandSpec spec;

    @Inject
    GitHubMcp gitHubMcp;

    @Override
    public Integer call() {
        gitHubMcp.use(mcp);
        String host = ConfigProvider.getConfig().getOptionalValue("quarkus.http.host", String.class).orElse("127.0.0.1");
        int actualPort = ConfigProvider.getConfig().getOptionalValue("quarkus.http.port", Integer.class).orElse(8080);
        spec.commandLine().getOut().println("Web interface on http://" + host + ":" + actualPort + "  (Ctrl+C to stop)");
        spec.commandLine().getOut().flush();
        Quarkus.waitForExit();
        return 0;
    }
}
