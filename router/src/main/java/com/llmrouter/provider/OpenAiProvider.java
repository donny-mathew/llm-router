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
import java.util.UUID;

@Component
public class OpenAiProvider implements LlmProvider {

    private static final String MODEL_ID = "gpt-4o";
    private static final double COST_PER_1K_PROMPT = 0.005;
    private static final double COST_PER_1K_COMPLETION = 0.015;

    private final WebClient webClient;

    public OpenAiProvider(@Qualifier("openAiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override public String getName() { return "openai"; }
    @Override public String getModelId() { return MODEL_ID; }
    @Override public String getTier() { return "powerful"; }
    @Override public double getCostPer1kPrompt() { return COST_PER_1K_PROMPT; }
    @Override public double getCostPer1kCompletion() { return COST_PER_1K_COMPLETION; }

    @Override
    public Mono<ProviderResponse> complete(ChatCompletionRequest request) {
        long start = System.currentTimeMillis();
        // Pin the model this adapter is priced for, and disable streaming (the router
        // parses a single JSON body). Otherwise the client's "model" passes straight through.
        ChatCompletionRequest body = new ChatCompletionRequest(
                MODEL_ID, request.messages(), request.temperature(), request.maxTokens(),
                false, request.user(), request.tools(), request.toolChoice(),
                request.responseFormat(), request.metadata());
        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OpenAiResponse.class)
                .map(r -> {
                    long latency = System.currentTimeMillis() - start;
                    ChatCompletionResponse response = new ChatCompletionResponse(
                            r.id(),
                            "chat.completion",
                            Instant.now().getEpochSecond(),
                            r.model(),
                            r.choices().stream()
                                    .map(c -> new ChatCompletionResponse.Choice(
                                            c.index(),
                                            c.message(),
                                            c.finishReason()))
                                    .toList(),
                            r.usage(),
                            null
                    );
                    return new ProviderResponse(response, latency);
                });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenAiResponse(
            String id,
            String model,
            List<OpenAiChoice> choices,
            Usage usage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenAiChoice(
            int index,
            ChatMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {}
}
