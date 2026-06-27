package com.llmrouter.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionResponse(
        String id,
        String object,
        long created,
        String model,
        List<Choice> choices,
        Usage usage,
        @JsonProperty("x_router_meta") RouterMeta xRouterMeta
) {
    public record Choice(
            int index,
            ChatMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {}
}
