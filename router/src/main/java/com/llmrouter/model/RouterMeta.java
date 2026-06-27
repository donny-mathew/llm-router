package com.llmrouter.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RouterMeta(
        @JsonProperty("complexity_score") double complexityScore,
        @JsonProperty("tier_assigned") String tierAssigned,
        @JsonProperty("provider_used") String providerUsed,
        @JsonProperty("model_used") String modelUsed,
        boolean rerouted,
        @JsonProperty("quality_score") Double qualityScore,
        @JsonProperty("latency_ms") long latencyMs,
        @JsonProperty("estimated_cost_usd") double estimatedCostUsd
) {}
