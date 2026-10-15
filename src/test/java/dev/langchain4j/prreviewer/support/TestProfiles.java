package dev.langchain4j.prreviewer.support;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/** Test profiles that put real services back in place of the test doubles. */
public final class TestProfiles {

    private TestProfiles() {}

    /** The real decision models; chat models and GitHub stay scripted. */
    public static class RealDecisionModels implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.arc.exclude-types", TestDecisionModels.class.getName());
        }
    }

    /** Real chat models, decision models and GitHub MCP server. */
    public static class RealServices implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.arc.exclude-types", String.join(",", TestChatModels.class.getName(),
                    TestDecisionModels.class.getName(), FixtureGitHubMcp.class.getName()));
        }
    }
}
