package dev.langchain4j.prreviewer.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.prreviewer.model.ReportDraft;
import dev.langchain4j.prreviewer.support.FakeDecisionModel;
import dev.langchain4j.prreviewer.support.TestData;
import dev.langchain4j.prreviewer.support.TestScenario;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The web interface with scripted models, the fake decision model and the GitHub fixtures of pull request 6610. */
@QuarkusTest
@TestProfile(WebInterfaceTest.Keys.class)
class WebInterfaceTest {

    /** Placeholder keys, so that both providers count as available; the models are scripted anyway. */
    public static class Keys implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("ANTHROPIC_API_KEY", "test", "OPENAI_API_KEY", "test");
        }
    }

    private static final Pattern TOKEN = Pattern.compile("name=\"csrf-token\" value=\"([^\"]+)\"");

    @Inject
    TestScenario scenario;

    String csrfCookie;
    String csrfToken;

    @BeforeEach
    void session() {
        scenario.reset();
        Response home = given().get("/");
        csrfCookie = home.getCookie("csrf-token");
        Matcher token = TOKEN.matcher(home.asString());
        assertThat(token.find()).isTrue();
        csrfToken = token.group(1);
    }

    private Response post(String path, Map<String, ?> form) {
        var request = given().redirects().follow(false).cookie("csrf-token", csrfCookie)
                .contentType("application/x-www-form-urlencoded")
                .formParam("csrf-token", csrfToken);
        form.forEach((name, value) -> {
            if (value instanceof List<?> values) {
                values.forEach(v -> request.formParam(name, v));
            } else {
                request.formParam(name, value);
            }
        });
        return request.post(path);
    }

    private String submit(String mode) {
        Response response = post("/reviews", Map.of("pr", "langchain4j/langchain4j#6610", "mode", mode,
                "provider", "ANTHROPIC", "decisionModel", "LOCAL", "maxCost", "2.0", "maxDebates", "3",
                "maxFiles", "50", "maxDiffTokens", "60000", "maxChunks", "4"));
        assertThat(response.statusCode()).isEqualTo(303);
        String location = response.getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private String awaitFragment(String path, Predicate<String> condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        String body = "";
        while (System.nanoTime() < deadline) {
            body = given().get(path).asString();
            if (condition.test(body)) {
                return body;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Timed out waiting on " + path + ", last answer: " + body);
    }

    private String awaitStatus(String id, String status) {
        return awaitFragment("/reviews/" + id + "/status", body -> body.contains("badge " + status));
    }

    @Test
    void home_page_offers_the_form_and_the_available_providers() {
        String home = given().get("/").asString();

        assertThat(home).contains("Review a pull request").contains("<option value=\"ANTHROPIC\"")
                .contains("<option value=\"OPENAI\"").contains("/_static/htmx.org/dist/htmx.min.js");
    }

    @Test
    void automatic_review_runs_and_its_report_is_rendered_and_downloadable() {
        String id = submit("AUTO");

        String status = awaitStatus(id, "DONE");
        Matcher report = Pattern.compile("href=\"/reports/([^\"]+)\"").matcher(status);
        assertThat(report.find()).as(status).isTrue();
        String directory = report.group(1);

        String page = given().get("/reviews/" + id).asString();
        assertThat(page).contains("prTriage").contains("reportWriter").contains("sse-connect=\"/reviews/" + id);

        String rendered = given().get("/reports/" + directory).asString();
        assertThat(rendered).contains("<h3>Scores</h3>").contains("<table>").contains("report.json");
        assertThat(given().get("/reports/" + directory + "/report.md").contentType()).startsWith("text/plain");
        Response trace = given().get("/reports/" + directory + "/trace.html");
        assertThat(trace.statusCode()).isEqualTo(200);
        assertThat(trace.getHeader("Content-Security-Policy")).isEqualTo("sandbox allow-scripts");
        assertThat(given().get("/").asString()).contains("/reports/" + directory);
    }

    @Test
    void events_replay_the_progress_and_end_with_done() {
        String id = submit("AUTO");
        awaitStatus(id, "DONE");

        String events = given().accept("text/event-stream").get("/reviews/" + id + "/events?from=0").asString();

        assertThat(events).contains("event:progress").contains("prTriage").contains("event:done");
    }

    @Test
    void interactive_review_asks_questions_and_takes_a_contest_then_an_approval() {
        scenario.decisionModel(new FakeDecisionModel(Map.of("correctnessReviewer", 0.9, "designReviewer", 0.7)));
        String id = submit("INTERACTIVE");

        awaitStatus(id, "WAITING_FOR_YOU");
        String questions = awaitFragment("/reviews/" + id + "/interaction", body -> body.contains("Questions"));
        assertThat(questions).contains("Should default methods ever call the model?");
        assertThat(post("/reviews/" + id + "/clarifications", Map.of("guidance", "Only Java 21 matters."))
                .statusCode()).isEqualTo(303);

        String decision = awaitFragment("/reviews/" + id + "/interaction", body -> body.contains("Your decision"));
        assertThat(decision).contains("value=\"F1\"").contains("<article class=\"report\">");
        post("/reviews/" + id + "/approval", Map.of("action", "contest", "contest", List.of("F1"),
                "notes", "there is a guard"));

        awaitFragment("/reviews/" + id + "/interaction", body -> body.contains("Your decision")
                && scenario.calls().stream().anyMatch(c -> c.agent().equals("debateJudge")));
        post("/reviews/" + id + "/approval", Map.of("action", "approve"));

        awaitStatus(id, "DONE");
        assertThat(scenario.calls()).anyMatch(c -> c.agent().equals("findingProsecutor"));
        assertThat(scenario.calls()).filteredOn(c -> c.agent().equals("correctnessReviewer"))
                .allSatisfy(c -> assertThat(c.user()).contains("Only Java 21 matters."));
    }

    @Test
    void approval_without_a_choice_is_refused_and_asked_again() {
        String id = submit("INTERACTIVE");
        awaitStatus(id, "WAITING_FOR_YOU");
        post("/reviews/" + id + "/clarifications", Map.of("guidance", ""));
        awaitFragment("/reviews/" + id + "/interaction", body -> body.contains("Your decision"));

        post("/reviews/" + id + "/approval", Map.of("action", "contest"));

        assertThat(given().get("/reviews/" + id + "/interaction").asString())
                .contains("Choose findings to contest or drop");
        post("/reviews/" + id + "/approval", Map.of("action", "approve"));
        awaitStatus(id, "DONE");
    }

    @Test
    void a_queued_review_can_be_cancelled() {
        String first = submit("INTERACTIVE");
        awaitStatus(first, "WAITING_FOR_YOU");
        String second = submit("AUTO");
        assertThat(given().get("/reviews/" + second + "/status").asString()).contains("badge QUEUED");

        post("/reviews/" + second + "/cancel", Map.of());

        awaitStatus(second, "CANCELLED");
        post("/reviews/" + first + "/clarifications", Map.of("guidance", ""));
        awaitFragment("/reviews/" + first + "/interaction", body -> body.contains("Your decision"));
        post("/reviews/" + first + "/approval", Map.of("action", "approve"));
        awaitStatus(first, "DONE");
    }

    @Test
    void report_text_is_escaped() {
        Map<String, Object> responses = TestData.defaultResponses();
        responses.put("reportWriter", new ReportDraft("<script>alert('pwned')</script> A focused refactoring.",
                List.of("<img src=x onerror=alert(1)>"), List.of(), List.of(), List.of()));
        scenario.responses(responses);
        String id = submit("AUTO");

        Matcher report = Pattern.compile("href=\"/reports/([^\"]+)\"").matcher(awaitStatus(id, "DONE"));
        assertThat(report.find()).isTrue();
        String rendered = given().get("/reports/" + report.group(1)).asString();

        assertThat(rendered).doesNotContain("<script>alert").doesNotContain("<img src=x")
                .contains("&lt;script&gt;alert");
    }

    @Test
    void requests_from_elsewhere_are_refused() {
        assertThat(given().header("Host", "evil.example").get("/").statusCode()).isIn(400, 403);
        assertThat(given().header("Origin", "https://evil.example").contentType("application/x-www-form-urlencoded")
                .formParam("pr", "a/b#1").post("/reviews").statusCode()).isEqualTo(403);
        assertThat(given().contentType("application/x-www-form-urlencoded").formParam("pr", "a/b#1")
                .post("/reviews").statusCode()).isEqualTo(400);
        assertThat(given().get("/reports/..%2F..%2Fpom.xml").statusCode()).isEqualTo(404);
        assertThat(given().get("/reports/langchain4j-langchain4j-6610/application.properties").statusCode())
                .isEqualTo(404);
    }

    @Test
    void invalid_pull_request_is_reported_on_the_form() {
        Response response = post("/reviews", Map.of("pr", "not-a-pr", "mode", "AUTO", "maxCost", "2.0",
                "maxDebates", "3", "maxFiles", "50", "maxDiffTokens", "60000", "maxChunks", "4"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.asString()).contains("Not a GitHub pull request");
    }
}
