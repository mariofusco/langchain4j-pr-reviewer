package dev.langchain4j.prreviewer.support;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Answers the router's yes/no questions with fixed probabilities per agent name, and records the requests. */
public final class FakeDecisionModel implements DecisionModel {

    private final Map<String, Double> probabilities;
    private final RuntimeException failure;
    private final List<DecisionRequest> requests = Collections.synchronizedList(new ArrayList<>());

    public FakeDecisionModel(Map<String, Double> probabilities) {
        this(probabilities, null);
    }

    private FakeDecisionModel(Map<String, Double> probabilities, RuntimeException failure) {
        this.probabilities = probabilities;
        this.failure = failure;
    }

    public static FakeDecisionModel failing() {
        return new FakeDecisionModel(Map.of(), new IllegalStateException("decision model down"));
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        requests.add(request);
        if (failure != null) {
            throw failure;
        }
        DecisionResponse.Builder response = DecisionResponse.builder().modelName("fake-decision");
        request.questions().forEach((name, question) -> {
            if (!(question instanceof YesNoQuestion)) {
                throw new IllegalArgumentException("Only yes/no questions expected, got " + question);
            }
            response.answer(name, YesNoAnswer.of(probabilities.getOrDefault(name, 0.0)));
        });
        return response.build();
    }

    public List<DecisionRequest> requests() {
        return List.copyOf(requests);
    }
}
