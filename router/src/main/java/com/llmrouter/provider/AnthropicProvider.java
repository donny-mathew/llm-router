package com.llmrouter.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.llmrouter.model.ChatCompletionRequest;
import com.llmrouter.model.ChatCompletionResponse;
import com.llmrouter.model.ChatMessage;
import com.llmrouter.model.Usage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class AnthropicProvider implements LlmProvider {

    private static final String MODEL_ID = "claude-sonnet-4-6";
    private static final double COST_PER_1K_PROMPT = 0.003;
    private static final double COST_PER_1K_COMPLETION = 0.015;

    private final WebClient webClient;

    public AnthropicProvider(@Qualifier("anthropicWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override public String getName() { return "anthropic"; }
    @Override public String getModelId() { return MODEL_ID; }
    @Override public String getTier() { return "powerful"; }
    @Override public double getCostPer1kPrompt() { return COST_PER_1K_PROMPT; }
    @Override public double getCostPer1kCompletion() { return COST_PER_1K_COMPLETION; }

    @Override
    public Mono<ProviderResponse> complete(ChatCompletionRequest request) {
        return completeWithModel(request, MODEL_ID);
    }

    public Mono<ProviderResponse> completeWithModel(ChatCompletionRequest request, String model) {
        long start = System.currentTimeMillis();

        // Extract system message — Anthropic puts it in a top-level field
        String systemPrompt = request.messages().stream()
                .filter(m -> "system".equals(m.role()))
                .map(m -> m.content() != null ? m.content().toString() : "")
                .findFirst()
                .orElse(null);

        List<ChatMessage> nonSystemMessages = request.messages().stream()
                .filter(m -> !"system".equals(m.role()))
                .toList();

        AnthropicRequest body = new AnthropicRequest(
                model,
                request.maxTokens() != null ? request.maxTokens() : 4096,
                systemPrompt,
                nonSystemMessages,
                request.temperature()
        );

        return webClient.post()
                .uri("/v1/messages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(AnthropicResponse.class)
                .map(r -> {
                    long latency = System.currentTimeMillis() - start;
                    String content = r.content().isEmpty() ? "" : r.content().get(0).text();
                    String finishReason = "end_turn".equals(r.stopReason()) ? "stop" : r.stopReason();
                    Usage usage = new Usage(
                            r.usage().inputTokens(),
                            r.usage().outputTokens(),
                            r.usage().inputTokens() + r.usage().outputTokens()
                    );
                    ChatCompletionResponse response = new ChatCompletionResponse(
                            r.id(),
                            "chat.completion",
                            Instant.now().getEpochSecond(),
                            r.model(),
                            List.of(new ChatCompletionResponse.Choice(0, new ChatMessage("assistant", content), finishReason)),
                            usage,
                            null
                    );
                    return new ProviderResponse(response, latency);
                });
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AnthropicRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            String system,
            List<ChatMessage> messages,
            Double temperature
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnthropicResponse(
            String id,
            String model,
            List<ContentBlock> content,
            @JsonProperty("stop_reason") String stopReason,
            AnthropicUsage usage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(String type, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnthropicUsage(
            @JsonProperty("input_tokens") int inputTokens,
            @JsonProperty("output_tokens") int outputTokens
    ) {}
}
