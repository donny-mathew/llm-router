package com.llmrouter.provider;

import com.llmrouter.model.ChatCompletionResponse;

public record ProviderResponse(
        ChatCompletionResponse response,
        long latencyMs
) {}
