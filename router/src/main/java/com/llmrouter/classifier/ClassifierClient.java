package com.llmrouter.classifier;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.llmrouter.config.RouterProperties;
import com.llmrouter.model.ChatCompletionRequest;
import com.llmrouter.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Component
public class ClassifierClient {

    private static final Logger log = LoggerFactory.getLogger(ClassifierClient.class);

    private static final Set<String> INSTRUCTION_KEYWORDS = Set.of(
            "analyze", "compare", "explain", "implement", "debug", "refactor",
            "evaluate", "synthesize", "critique", "design", "architect", "optimize"
    );
    private static final Set<String> MULTI_STEP_KEYWORDS = Set.of(
            "step 1", "first", "then", "finally", "also", "next",
            "additionally", "furthermore", "lastly", "subsequently"
    );

    private final WebClient webClient;
    private final RouterProperties props;

    public ClassifierClient(RouterProperties props) {
        this.props = props;
        this.webClient = WebClient.builder()
                .baseUrl(props.classifier().sidecarUrl())
                .build();
    }

    public Mono<ClassifierResponse> score(ChatCompletionRequest request) {
        ScoreRequest body = new ScoreRequest(
                request.messages(),
                request.maxTokens(),
                request.temperature(),
                props.tiers().cheapMax(),
                props.tiers().midMax()
        );

        return webClient.post()
                .uri("/score")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(ClassifierResponse.class)
                .timeout(Duration.ofMillis(props.classifier().timeoutMs()))
                .onErrorResume(ex -> {
                    log.warn("Classifier sidecar unavailable ({}), using heuristic fallback", ex.getMessage());
                    return Mono.just(heuristicFallback(request));
                });
    }

    /**
     * Local Java heuristic — mirrors the Python weighted-sum scorer.
     * Invoked when the sidecar is unreachable so routing never stalls.
     */
    private ClassifierResponse heuristicFallback(ChatCompletionRequest request) {
        String allText = request.messages().stream()
                .map(ChatMessage::content)
                .filter(c -> c instanceof String)
                .map(Object::toString)
                .reduce("", (a, b) -> a + " " + b)
                .toLowerCase();

        double totalCharsNorm   = Math.min(1.0, allText.length() / 4000.0);
        double codeBlocks       = Math.min(1.0, countOccurrences(allText, "```") / 2.0 / 5.0);
        double instructionDepth = Math.min(1.0, INSTRUCTION_KEYWORDS.stream()
                .filter(allText::contains).count() / (double) INSTRUCTION_KEYWORDS.size());
        double multiStep        = Math.min(1.0, MULTI_STEP_KEYWORDS.stream()
                .filter(allText::contains).count() / (double) MULTI_STEP_KEYWORDS.size());
        double messageCountNorm = Math.min(1.0, request.messages().size() / 20.0);

        double score = codeBlocks      * 0.20
                + instructionDepth     * 0.25
                + multiStep            * 0.15
                + totalCharsNorm       * 0.15
                + messageCountNorm     * 0.10;

        score = Math.min(1.0, score);

        String tier;
        if (score <= props.tiers().cheapMax()) tier = "cheap";
        else if (score <= props.tiers().midMax()) tier = "mid";
        else tier = "powerful";

        log.debug("Heuristic fallback score={} tier={}", score, tier);
        return new ClassifierResponse(score, tier);
    }

    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    record ScoreRequest(
            List<ChatMessage> messages,
            @JsonProperty("max_tokens") Integer maxTokens,
            Double temperature,
            @JsonProperty("cheap_max") double cheapMax,
            @JsonProperty("mid_max") double midMax
    ) {}

    public record ClassifierResponse(double score, String tier) {}
}
