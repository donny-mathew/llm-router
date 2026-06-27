package com.llmrouter.storage;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestLog {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "request_json", columnDefinition = "TEXT")
    private String requestJson;

    @Column(name = "model_requested")
    private String modelRequested;

    @Column(name = "complexity_score")
    private double complexityScore;

    @Column(name = "tier_assigned")
    private String tierAssigned;

    @Column(name = "provider_used")
    private String providerUsed;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "response_json", columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "prompt_tokens")
    private int promptTokens;

    @Column(name = "completion_tokens")
    private int completionTokens;

    @Column(name = "latency_ms")
    private long latencyMs;

    @Column(name = "estimated_cost_usd")
    private double estimatedCostUsd;

    @Column(name = "quality_score")
    private Double qualityScore;

    @Column(name = "rerouted")
    private boolean rerouted;

    @Column(name = "reroute_reason")
    private String rerouteReason;

    @Column(name = "judge_verdict", columnDefinition = "TEXT")
    private String judgeVerdict;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }
}
