package dev.langchain4j.prreviewer.web;

import dev.langchain4j.prreviewer.app.ReviewRequest;
import dev.langchain4j.prreviewer.config.ReviewDefaults;
import dev.langchain4j.prreviewer.github.PrUrl;
import dev.langchain4j.prreviewer.hitl.ApprovalRules;
import dev.langchain4j.prreviewer.model.ApprovalDecision.Decision;
import dev.langchain4j.prreviewer.model.ApprovalDecision;
import dev.langchain4j.prreviewer.model.DecisionProvider;
import dev.langchain4j.prreviewer.model.Finding;
import dev.langchain4j.prreviewer.model.ModelProvider;
import dev.langchain4j.prreviewer.model.ModelTier;
import dev.langchain4j.prreviewer.model.PrRef;
import dev.langchain4j.prreviewer.model.ReviewMode;
import dev.langchain4j.prreviewer.model.ReviewOptions;
import dev.langchain4j.prreviewer.models.DecisionModels;
import dev.langchain4j.prreviewer.models.ModelCatalog;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

/** The home page and the review runs: submitting, following, answering, cancelling. */
@Path("/")
public class ReviewResource {

    @Inject
    ReviewQueue queue;

    @Inject
    ReportHistory history;

    @Inject
    ReviewDefaults defaults;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        return index("", null, defaults.options(), defaults.config().mode());
    }

    @POST
    @Path("reviews")
    @Produces(MediaType.TEXT_HTML)
    public Response submit(@RestForm String pr, @RestForm String mode, @RestForm String provider,
                           @RestForm String decisionModel, @RestForm String modelTier, @RestForm String routerThreshold,
                           @RestForm boolean deep, @RestForm double maxCost, @RestForm int maxDebates,
                           @RestForm int maxFiles, @RestForm int maxDiffTokens, @RestForm int maxChunks) {
        ReviewMode reviewMode = parse(ReviewMode.class, mode, ReviewMode.AUTO);
        ModelProvider modelProvider = parse(ModelProvider.class, provider, defaults.config().provider());
        DecisionProvider decision = parse(DecisionProvider.class, decisionModel, defaults.config().decisionModel());
        ModelTier tier = modelTier == null || modelTier.isBlank() ? null : parse(ModelTier.class, modelTier, null);
        double threshold = routerThreshold == null || routerThreshold.isBlank()
                ? decision.defaultRouterThreshold()
                : Double.parseDouble(routerThreshold);
        ReviewOptions options = new ReviewOptions(deep, maxFiles, maxDiffTokens, tier, maxDebates, threshold, maxCost,
                maxChunks, modelProvider, decision);
        String error = null;
        PrRef ref = null;
        try {
            ref = PrUrl.parse(pr);
        } catch (IllegalArgumentException e) {
            error = e.getMessage();
        }
        if (error == null && !ModelCatalog.available(modelProvider)) {
            error = modelProvider + " needs " + modelProvider.apiKeyVariable() + " on the server";
        } else if (error == null && !DecisionModels.available(decision)) {
            error = decision + " needs " + decision.apiKeyVariable() + " on the server";
        } else if (error == null && (threshold <= 0 || threshold >= 1)) {
            error = "The router threshold must be between 0 and 1, exclusive";
        }
        if (error != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(index(pr, error, options, reviewMode).render()).type(MediaType.TEXT_HTML).build();
        }
        WebRun run = queue.submit(new ReviewRequest(ref, reviewMode, options));
        return seeOther("/reviews/" + run.id());
    }

    @GET
    @Path("reviews/{id}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance run(@RestPath String id) {
        WebRun run = find(id);
        return Pages.run(new Views.RunPage(run, interaction(run)));
    }

    @GET
    @Path("reviews/{id}/status")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance status(@RestPath String id) {
        return Pages.status(find(id));
    }

    @GET
    @Path("reviews/{id}/interaction")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance interaction(@RestPath String id) {
        return Pages.interaction(interaction(find(id)));
    }

    @GET
    @Path("reviews/{id}/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<OutboundSseEvent> events(@RestPath String id, @RestQuery int from, @Context Sse sse) {
        WebRun run = find(id);
        return Multi.createFrom().emitter(emitter -> run.subscribe(emitter, sse, from));
    }

    @POST
    @Path("reviews/{id}/clarifications")
    public Response clarify(@RestPath String id, @RestForm String guidance) {
        WebRun run = find(id);
        if (run.interaction() instanceof Interaction.Clarification clarification) {
            clarification.answer().complete(guidance == null ? "" : guidance.strip());
        }
        return seeOther("/reviews/" + id);
    }

    @POST
    @Path("reviews/{id}/approval")
    public Response approve(@RestPath String id, @RestForm String action, @RestForm List<String> contest,
                            @RestForm List<String> drop, @RestForm String notes) {
        WebRun run = find(id);
        if (run.interaction() instanceof Interaction.Approval approval) {
            Set<String> known = approval.findings().stream().map(Finding::id).collect(Collectors.toSet());
            String text = notes == null ? "" : notes.strip();
            Optional<ApprovalDecision> decision = decision(action, contest, drop, text, known);
            Optional<String> refusal = decision.isEmpty()
                    ? Optional.of("Choose findings to contest or drop, or write the notes of an edit.")
                    : ApprovalRules.refusal(decision.get(), approval.debatesAffordable(), approval.spentUsd(),
                            approval.capUsd());
            if (refusal.isPresent()) {
                run.refuse(refusal.get());
            } else {
                approval.answer().complete(decision.get());
            }
        }
        return seeOther("/reviews/" + id);
    }

    @POST
    @Path("reviews/{id}/cancel")
    public Response cancel(@RestPath String id) {
        queue.cancel(id);
        return seeOther("/");
    }

    static Optional<ApprovalDecision> decision(String action, List<String> contest, List<String> drop, String notes,
                                               Set<String> known) {
        List<String> contested = ids(contest, known);
        List<String> dropped = ids(drop, known);
        return switch (action == null ? "" : action) {
            case "approve" -> Optional.of(ApprovalDecision.approved());
            case "edit" -> notes.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new ApprovalDecision(Decision.EDIT, notes, List.of(), List.of()));
            case "reject" -> Optional.of(new ApprovalDecision(Decision.REJECTED, notes, List.of(), List.of()));
            case "contest" -> contested.isEmpty() && dropped.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new ApprovalDecision(Decision.CONTEST, notes, contested, dropped));
            default -> Optional.empty();
        };
    }

    private static List<String> ids(List<String> values, Set<String> known) {
        return values == null ? List.of() : values.stream().filter(known::contains).distinct().toList();
    }

    private WebRun find(String id) {
        return queue.run(id).orElseThrow(() -> new NotFoundException("No review " + id));
    }

    private Views.InteractionView interaction(WebRun run) {
        Interaction interaction = run.interaction();
        if (interaction instanceof Interaction.Clarification clarification) {
            return new Views.InteractionView(run.id(), "clarification", clarification.questions(), "", List.of(), "",
                    "", true, run.interactionError());
        }
        if (interaction instanceof Interaction.Approval approval) {
            return new Views.InteractionView(run.id(), "approval", List.of(),
                    MarkdownRenderer.render(approval.report().markdown()),
                    approval.findings().stream().map(Views.FindingRow::of).toList(),
                    String.format(Locale.ROOT, "%.2f", approval.spentUsd()),
                    String.format(Locale.ROOT, "%.2f", approval.capUsd()), approval.debatesAffordable(),
                    run.interactionError());
        }
        return Views.InteractionView.none(run.id());
    }

    private TemplateInstance index(String pr, String error, ReviewOptions options, ReviewMode mode) {
        return Pages.index(new Views.IndexPage(pr, error,
                choices(ReviewMode.values(), mode, value -> true),
                choices(ModelProvider.values(), options.provider(), ModelCatalog::available),
                choices(DecisionProvider.values(), options.decisionModel(), DecisionModels::available),
                choices(ModelTier.values(), options.forcedTier(), value -> true),
                options, defaults.config().routerThreshold().map(String::valueOf).orElse(""),
                queue.runs(), history.entries(20)));
    }

    private static <E extends Enum<E>> List<Views.Choice> choices(E[] values, E selected,
                                                                  java.util.function.Predicate<E> available) {
        return Arrays.stream(values).filter(available)
                .map(v -> new Views.Choice(v.name(), v.name().toLowerCase(Locale.ROOT).replace('_', ' '), v == selected))
                .toList();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
    }

    private static Response seeOther(String path) {
        return Response.seeOther(URI.create(path)).build();
    }
}
