package dev.langchain4j.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.prreviewer.model.PrRef;
import dev.langchain4j.prreviewer.model.PullRequest;
import dev.langchain4j.prreviewer.support.TestProfiles;
import dev.langchain4j.service.tool.ToolProviderRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** Reads a real pull request through the read-only GitHub MCP server configured for Docker. */
@QuarkusTest
@TestProfile(TestProfiles.RealServices.class)
@EnabledIf("dev.langchain4j.prreviewer.support.Availability#githubMcp")
class GitHubMcpIT {

    @Inject
    GitHubMcp github;

    @Test
    void reads_a_pull_request_and_offers_only_read_tools() {
        PullRequest pr = new PullRequestReader(github.gateway()).read(new PrRef("langchain4j", "langchain4j", 6610));

        assertThat(pr.title()).isEqualTo("Decide in one place which AI Service methods call the model");
        assertThat(DiffParser.parse(pr.rawDiff())).hasSize(6);
        assertThat(pr.ciStatus().total()).isPositive();

        Set<String> serverTools = github.client().listTools().stream().map(ToolSpecification::name)
                .collect(Collectors.toSet());
        assertThat(serverTools).doesNotContain("create_pull_request", "merge_pull_request", "add_issue_comment",
                "pull_request_review_write", "create_or_update_file", "push_files");

        Set<String> agentTools = github.readTools(6)
                .provideTools(ToolProviderRequest.builder()
                        .invocationContext(InvocationContext.builder().build())
                        .userMessage(UserMessage.from("x"))
                        .build())
                .tools().keySet().stream().map(ToolSpecification::name).collect(Collectors.toSet());
        assertThat(agentTools).containsExactlyInAnyOrderElementsOf(GitHubMcp.READ_TOOLS);
    }
}
