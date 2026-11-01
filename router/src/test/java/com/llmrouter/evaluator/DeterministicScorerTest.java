package com.llmrouter.evaluator;

import com.llmrouter.model.ChatCompletionResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.llmrouter.TestData.request;
import static com.llmrouter.TestData.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DeterministicScorerTest {

    private final DeterministicScorer scorer = new DeterministicScorer();

    @Test
    void fastCompleteAnswerScoresPerfectly() {
        double score = scorer.score(request("What is the capital of France?"),
                response("The capital of France is Paris.", "stop"), 500);
        assertThat(score).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void refusalIsPenalisedBelowDefaultQualityGate() {
        double score = scorer.score(request("Summarise this contract for me please"),
                response("I'm unable to help with that request, sorry about that.", "stop"), 500);
        assertThat(score).isLessThan(0.65);
    }

    @Test
    void truncatedResponseLosesFinishReasonCredit() {
        double complete = scorer.score(request("hi"), response("Hello there, how can I help?", "stop"), 500);
        double truncated = scorer.score(request("hi"), response("Hello there, how can I help?", "length"), 500);
        assertThat(complete - truncated).isCloseTo(0.20 * 0.7, within(1e-9));
    }

    @Test
    void slowResponseIsPenalisedButFloored() {
        double fast = scorer.score(request("hi"), response("Hello there!", "stop"), 1_000);
        double verySlow = scorer.score(request("hi"), response("Hello there!", "stop"), 60_000);
        assertThat(fast - verySlow).isCloseTo(0.10 * 0.5, within(1e-9));
    }

    @Test
    void emptyChoicesScoreZero() {
        var empty = new ChatCompletionResponse("id", "chat.completion", 0L, "m", List.of(), null, null);
        assertThat(scorer.score(request("hi"), empty, 100)).isZero();
    }
}
