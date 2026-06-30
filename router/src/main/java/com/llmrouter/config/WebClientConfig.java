package com.llmrouter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Bean("openAiWebClient")
    public WebClient openAiWebClient(RouterProperties props) {
        return WebClient.builder()
                .baseUrl("https://api.openai.com")
                .defaultHeader("Authorization", "Bearer " + props.providers().openaiApiKey())
                .defaultHeader("Content-Type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    @Bean("anthropicWebClient")
    public WebClient anthropicWebClient(RouterProperties props) {
        return WebClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", props.providers().anthropicApiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    @Bean("ollamaWebClient")
    public WebClient ollamaWebClient(RouterProperties props) {
        return WebClient.builder()
                .baseUrl(props.providers().ollamaBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }
}
