package com.llmrouter.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

@Component
public class OllamaProvider implements LlmProvider {

    // Default cheap model — can be overridden per-tier in ProviderRegistry
    private final String modelId;
    private final String tier;
    private final WebClient webClient;

    public OllamaProvider(
            @Qualifier("ollamaWebClient") WebClient webClient) {
        this(webClient, "llama3.2:3b", "cheap");
    }

    public OllamaProvider(WebClient webClient, String modelId, String tier) {
        this.webClient = webClient;
        this.modelId = modelId;
        this.tier = tier;
    }

    @Override public String getName() { return "ollama"; }
    @Override public String getModelId() { return modelId; }
    @Override public String getTier() { return tier; }
    // Local model — no API cost
    @Override public double getCostPer1kPrompt() { return 0.0; }
    @Override public double getCostPer1kCompletion() { return 0.0; }

    @Override
    public Mono<ProviderResponse> complete(ChatCompletionRequest request) {
        long start = System.currentTimeMillis();

        // Build an OpenAI-compatible request body targeting the configured model
        var body = new OllamaRequest(modelId, request.messages(), request.temperature(), request.maxTokens());

        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OllamaResponse.class)
                .map(r -> {
                    long latency = System.currentTimeMillis() - start;
                    Usage usage = new Usage(
                            r.usage() != null ? r.usage().promptTokens() : 0,
                            r.usage() != null ? r.usage().completionTokens() : 0,
                            r.usage() != null ? r.usage().totalTokens() : 0
                    );
                    ChatCompletionResponse response = new ChatCompletionResponse(
                            r.id() != null ? r.id() : "ollama-" + System.currentTimeMillis(),
                            "chat.completion",
                            Instant.now().getEpochSecond(),
                            modelId,
                            r.choices().stream()
                                    .map(c -> new ChatCompletionResponse.Choice(
                                            c.index(), c.message(), c.finishReason()))
                                    .toList(),
                            usage,
                            null
                    );
                    return new ProviderResponse(response, latency);
                });
    }

    record OllamaRequest(
            String model,
            List<ChatMessage> messages,
            Double temperature,
            @JsonProperty("max_tokens") Integer maxTokens
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaResponse(
            String id,
            String model,
            List<OllamaChoice> choices,
            OllamaUsage usage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaChoice(
            int index,
            ChatMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaUsage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {}
}
