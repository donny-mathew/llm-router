package com.llmrouter.provider;

import com.llmrouter.model.ChatCompletionRequest;
import reactor.core.publisher.Mono;

public interface LlmProvider {

    String getName();

    String getModelId();

    String getTier();

    double getCostPer1kPrompt();

    double getCostPer1kCompletion();

    Mono<ProviderResponse> complete(ChatCompletionRequest request);

    default double estimateCost(int promptTokens, int completionTokens) {
        return (promptTokens / 1000.0 * getCostPer1kPrompt())
                + (completionTokens / 1000.0 * getCostPer1kCompletion());
    }
}
