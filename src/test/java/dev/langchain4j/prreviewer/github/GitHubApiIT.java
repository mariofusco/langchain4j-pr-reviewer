package dev.langchain4j.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.prreviewer.model.PrRef;
import dev.langchain4j.prreviewer.model.PullRequest;
import dev.langchain4j.prreviewer.support.TestProfiles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** Reads a real pull request and repository through the hub4j client, with the token of the environment. */
@QuarkusTest
@TestProfile(TestProfiles.RealServices.class)
@EnabledIf("dev.langchain4j.prreviewer.support.Availability#githubToken")
class GitHubApiIT {

    @Inject
    GitHubReads github;

    @Inject
    GitHubTools tools;

    @Test
    void reads_a_pull_request_and_offers_only_read_tools() {
        PullRequest pr = new PullRequestReader(github).read(new PrRef("langchain4j", "langchain4j", 6610));

        assertThat(pr.title()).isEqualTo("Decide in one place which AI Service methods call the model");
        assertThat(DiffParser.parse(pr.rawDiff())).hasSize(6);
        assertThat(pr.ciStatus().total()).isPositive();

        String file = tools.getFileContents("langchain4j", "langchain4j",
                "langchain4j/src/main/java/dev/langchain4j/service/AiServiceValidation.java", pr.headSha());
        assertThat(file).contains("class AiServiceValidation");
        assertThat(tools.getFileContents("langchain4j", "langchain4j", "langchain4j/src/main/java/dev/langchain4j/service", pr.headSha()))
                .contains("AiServiceValidation.java");
        assertThat(github.searchCode("AiServiceValidation repo:langchain4j/langchain4j")).isNotEmpty();
        assertThat(tools.listCommits("langchain4j", "langchain4j", "README.md", null)).isNotBlank();

        assertThat(ToolSpecifications.toolSpecificationsFrom(GitHubTools.class)).extracting(ToolSpecification::name)
                .containsExactlyInAnyOrderElementsOf(GitHubTools.TOOL_NAMES);
    }
}
