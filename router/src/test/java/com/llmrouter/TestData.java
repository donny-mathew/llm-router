package com.llmrouter;

import com.llmrouter.model.ChatCompletionRequest;
import com.llmrouter.model.ChatCompletionResponse;
import com.llmrouter.model.ChatMessage;
import com.llmrouter.model.Usage;

import java.util.List;

public final class TestData {

    private TestData() {}

    public static ChatCompletionRequest request(String userText) {
        return new ChatCompletionRequest("auto", List.of(new ChatMessage("user", userText)),
                null, null, null, null, null, null, null, null);
    }

    public static ChatCompletionResponse response(String content, String finishReason) {
        return new ChatCompletionResponse("resp-1", "chat.completion", 0L, "test-model",
                List.of(new ChatCompletionResponse.Choice(0, new ChatMessage("assistant", content), finishReason)),
                new Usage(1000, 1000, 2000), null);
    }
}
