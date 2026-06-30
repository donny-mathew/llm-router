package com.llmrouter.provider;

import com.llmrouter.model.ChatCompletionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class ProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProviderRegistry.class);

    private final Map<String, List<LlmProvider>> tierMap;

    public ProviderRegistry(
            OpenAiProvider openAi,
            AnthropicProvider anthropic,
            @Qualifier("ollamaWebClient") WebClient ollamaWebClient) {

        OllamaProvider ollamaCheap = new OllamaProvider(ollamaWebClient, "llama3.2:3b", "cheap");
        OllamaProvider ollamaMid   = new OllamaProvider(ollamaWebClient, "llama3.1:8b", "mid");

        // Anthropic haiku for mid tier — cheaper than sonnet
        AnthropicHaikuProvider haikuMid = new AnthropicHaikuProvider(anthropic);

        this.tierMap = Map.of(
                "cheap",    List.of(ollamaCheap),
                "mid",      List.of(ollamaMid, haikuMid),
                "powerful", List.of(anthropic, openAi)
        );
    }

    public Mono<ProviderResponse> complete(String tier, ChatCompletionRequest request) {
        List<LlmProvider> providers = tierMap.getOrDefault(tier, tierMap.get("powerful"));
        return tryInOrder(providers, 0, request);
    }

    private Mono<ProviderResponse> tryInOrder(List<LlmProvider> providers, int index, ChatCompletionRequest request) {
        if (index >= providers.size()) {
            return Mono.error(new RuntimeException("All providers in tier exhausted"));
        }
        LlmProvider provider = providers.get(index);
        log.debug("Trying provider {} ({})", provider.getName(), provider.getModelId());
        return provider.complete(request)
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.warn("Provider {} failed ({}), falling back", provider.getName(), ex.getStatusCode());
                    return tryInOrder(providers, index + 1, request);
                })
                .onErrorResume(Exception.class, ex -> {
                    log.warn("Provider {} error: {}, falling back", provider.getName(), ex.getMessage());
                    return tryInOrder(providers, index + 1, request);
                });
    }

    public LlmProvider getFirstProvider(String tier) {
        List<LlmProvider> providers = tierMap.getOrDefault(tier, tierMap.get("powerful"));
        return providers.get(0);
    }

    public String upgradeTier(String current) {
        return switch (current) {
            case "cheap" -> "mid";
            case "mid"   -> "powerful";
            default      -> "powerful";
        };
    }

    /**
     * Thin wrapper that overrides model ID and costs for claude-haiku on the mid tier,
     * reusing the same Anthropic WebClient from the injected AnthropicProvider.
     */
    static class AnthropicHaikuProvider implements LlmProvider {

        private static final String MODEL_ID = "claude-haiku-4-5";
        private static final double COST_PER_1K_PROMPT = 0.00025;
        private static final double COST_PER_1K_COMPLETION = 0.00125;

        private final AnthropicProvider delegate;

        AnthropicHaikuProvider(AnthropicProvider delegate) {
            this.delegate = delegate;
        }

        @Override public String getName() { return "anthropic"; }
        @Override public String getModelId() { return MODEL_ID; }
        @Override public String getTier() { return "mid"; }
        @Override public double getCostPer1kPrompt() { return COST_PER_1K_PROMPT; }
        @Override public double getCostPer1kCompletion() { return COST_PER_1K_COMPLETION; }

        @Override
        public Mono<ProviderResponse> complete(ChatCompletionRequest request) {
            // Reuse delegate but with haiku model substituted
            return delegate.completeWithModel(request, MODEL_ID);
        }
    }
}
