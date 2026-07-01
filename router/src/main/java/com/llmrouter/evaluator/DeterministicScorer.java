package com.llmrouter.evaluator;

import com.llmrouter.model.ChatCompletionRequest;
import com.llmrouter.model.ChatCompletionResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeterministicScorer {

    private static final List<String> REFUSAL_PHRASES = List.of(
            "i cannot", "i'm unable", "i am unable", "as an ai", "as a language model",
            "i don't have access", "i do not have access", "i'm not able", "i am not able",
            "that's not something i can", "this is not something i can"
    );

    /**
     * Returns a 0..1 quality score based on response content, finish reason, and latency.
     * No LLM calls — runs on every response at negligible cost.
     */
    public double score(ChatCompletionRequest request, ChatCompletionResponse response, long latencyMs) {
        if (response.choices() == null || response.choices().isEmpty()) {
            return 0.0;
        }

        var choice = response.choices().get(0);
        String content = choice.message() != null && choice.message().content() != null
                ? choice.message().content().toString()
                : "";

        // Length heuristic — very short responses are suspicious
        String lastUserMessage = request.messages().reversed().stream()
                .filter(m -> "user".equals(m.role()))
                .map(m -> m.content() != null ? m.content().toString() : "")
                .findFirst()
                .orElse("");
        double expectedMin = Math.min(50.0, lastUserMessage.length() * 0.3);
        double lengthScore = Math.min(1.0, content.length() / Math.max(expectedMin, 1.0));

        // Refusal detection
        String lower = content.toLowerCase();
        double refusalScore = REFUSAL_PHRASES.stream().anyMatch(lower::contains) ? 0.0 : 1.0;

        // Finish reason
        double finishScore = "stop".equals(choice.finishReason()) ? 1.0 : 0.3;

        // Latency penalty: <2s=1.0, >10s=0.5, linear between
        double latencyScore = latencyMs <= 2000
                ? 1.0
                : Math.max(0.5, 1.0 - (latencyMs - 2000) / 16000.0);

        return (lengthScore * 0.30)
                + (refusalScore * 0.40)
                + (finishScore  * 0.20)
                + (latencyScore * 0.10);
    }
}
