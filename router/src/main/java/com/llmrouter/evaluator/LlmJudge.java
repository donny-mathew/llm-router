package com.llmrouter.evaluator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmrouter.model.ChatCompletionRequest;
import com.llmrouter.model.ChatCompletionResponse;
import com.llmrouter.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class LlmJudge {

    private static final Logger log = LoggerFactory.getLogger(LlmJudge.class);

    private static final String JUDGE_MODEL = "claude-haiku-4-5";

    private static final String SYSTEM_PROMPT = """
            You are a strict quality evaluator for LLM responses.
            Given an original request and a response, rate the response on four dimensions.
            Respond ONLY with valid JSON — no preamble, no markdown fences.
            JSON schema:
            {
              "relevance":    <0-10>,
              "completeness": <0-10>,
              "accuracy":     <0-10>,
              "format":       <0-10>,
              "overall":      <0-10>,
              "verdict":      "pass" | "fail",
              "reason":       "<one sentence>"
            }
            A verdict of "fail" means the response should be retried with a stronger model.
            """;

    private final WebClient anthropicWebClient;
    private final ObjectMapper objectMapper;

    public LlmJudge(
            @Qualifier("anthropicWebClient") WebClient anthropicWebClient,
            ObjectMapper objectMapper) {
        this.anthropicWebClient = anthropicWebClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates a response using claude-haiku as judge.
     * Only called when the deterministic score falls below the quality threshold.
     * Returns a {@link JudgeVerdict} — never throws; errors produce a "pass" to avoid infinite reroute loops.
     */
    public Mono<JudgeVerdict> evaluate(ChatCompletionRequest request, ChatCompletionResponse response) {
        String userContent = buildUserContent(request, response);

        AnthropicRequest body = new AnthropicRequest(
                JUDGE_MODEL,
                512,
                SYSTEM_PROMPT,
                List.of(new ChatMessage("user", userContent)),
                0.0
        );

        return anthropicWebClient.post()
                .uri("/v1/messages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(AnthropicResponse.class)
                .flatMap(r -> {
                    String raw = r.content().isEmpty() ? "{}" : r.content().get(0).text();
                    return parseVerdict(raw);
                })
                .onErrorResume(ex -> {
                    log.warn("LLM judge failed: {} — defaulting to pass", ex.getMessage());
                    return Mono.just(new JudgeVerdict(10, 10, 10, 10, 10, "pass", "judge unavailable"));
                });
    }

    private String buildUserContent(ChatCompletionRequest request, ChatCompletionResponse response) {
        String lastUser = request.messages().reversed().stream()
                .filter(m -> "user".equals(m.role()))
                .map(m -> m.content() != null ? m.content().toString() : "")
                .findFirst().orElse("(no user message)");

        String assistantReply = response.choices() != null && !response.choices().isEmpty()
                && response.choices().get(0).message() != null
                ? response.choices().get(0).message().content().toString()
                : "(empty response)";

        return "REQUEST:\n" + lastUser + "\n\nRESPONSE:\n" + assistantReply;
    }

    private Mono<JudgeVerdict> parseVerdict(String raw) {
        try {
            return Mono.just(objectMapper.readValue(raw, JudgeVerdict.class));
        } catch (Exception e) {
            log.warn("Failed to parse judge verdict JSON: {}", e.getMessage());
            return Mono.just(new JudgeVerdict(5, 5, 5, 5, 5, "pass", "parse error"));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JudgeVerdict(
            int relevance,
            int completeness,
            int accuracy,
            int format,
            int overall,
            String verdict,
            String reason
    ) {
        public boolean isFail() {
            return "fail".equalsIgnoreCase(verdict);
        }
    }

    record AnthropicRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            String system,
            List<ChatMessage> messages,
            Double temperature
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnthropicResponse(List<ContentBlock> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(String type, String text) {}
}
