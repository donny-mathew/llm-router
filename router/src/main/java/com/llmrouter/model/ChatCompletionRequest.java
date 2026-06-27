package com.llmrouter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        Double temperature,
        @JsonProperty("max_tokens") Integer maxTokens,
        Boolean stream,
        String user,
        List<Object> tools,
        @JsonProperty("tool_choice") Object toolChoice,
        @JsonProperty("response_format") Object responseFormat,
        Map<String, Object> metadata
) {
    public ChatCompletionRequest {
        if (stream == null) stream = false;
    }
}
