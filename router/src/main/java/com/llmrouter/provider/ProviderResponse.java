package com.llmrouter.provider;

import com.llmrouter.model.ChatCompletionResponse;

/**
 * A provider's completion plus the metadata the router needs.
 *
 * @param provider the provider that actually served the response. Adapters leave it null;
 *                 {@link ProviderRegistry} stamps it, so fallbacks are attributed correctly.
 */
public record ProviderResponse(
        ChatCompletionResponse response,
        long latencyMs,
        LlmProvider provider
) {
    public ProviderResponse(ChatCompletionResponse response, long latencyMs) {
        this(response, latencyMs, null);
    }

    public ProviderResponse withProvider(LlmProvider servedBy) {
        return new ProviderResponse(response, latencyMs, servedBy);
    }
}
