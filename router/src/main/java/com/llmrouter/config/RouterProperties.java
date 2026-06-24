package com.llmrouter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "router")
public record RouterProperties(
        Providers providers,
        Tiers tiers,
        QualityGate qualityGate,
        Classifier classifier
) {

    public record Providers(
            String openaiApiKey,
            String anthropicApiKey,
            String ollamaBaseUrl
    ) {}

    public record Tiers(
            double cheapMax,
            double midMax
    ) {}

    public record QualityGate(
            double threshold,
            boolean enableReroute,
            int rerouteMaxAttempts
    ) {}

    public record Classifier(
            String sidecarUrl,
            int timeoutMs
    ) {}
}
