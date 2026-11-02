package com.llmrouter.provider;

import com.llmrouter.model.ChatCompletionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static com.llmrouter.TestData.request;
import static com.llmrouter.TestData.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderRegistryTest {

    private final OpenAiProvider openAi = mock(OpenAiProvider.class);
    private final AnthropicProvider anthropic = mock(AnthropicProvider.class);

    /** Ollama backed by a WebClient that always answers 503, i.e. local model is down. */
    private final WebClient ollamaDown = WebClient.builder()
            .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()))
            .build();

    private final ProviderRegistry registry = new ProviderRegistry(openAi, anthropic, ollamaDown);

    @Test
    void fallbackResponseIsAttributedToTheProviderThatServedIt() {
        ChatCompletionRequest req = request("hello");
        when(anthropic.completeWithModel(any(), eq("claude-haiku-4-5")))
                .thenReturn(Mono.just(new ProviderResponse(response("hi!", "stop"), 42)));

        StepVerifier.create(registry.complete("mid", req))
                .assertNext(r -> {
                    // Ollama (first in the mid tier) failed, so Haiku must be credited, not Ollama
                    assertThat(r.provider().getName()).isEqualTo("anthropic");
                    assertThat(r.provider().getModelId()).isEqualTo("claude-haiku-4-5");
                    assertThat(r.latencyMs()).isEqualTo(42);
                })
                .verifyComplete();
    }

    @Test
    void errorsWhenEveryProviderInTierFails() {
        when(anthropic.complete(any())).thenReturn(Mono.error(new RuntimeException("boom")));
        when(openAi.complete(any())).thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(registry.complete("powerful", request("hello")))
                .expectErrorMessage("All providers in tier exhausted")
                .verify();
    }

    @Test
    void upgradeTierClimbsAndCapsAtPowerful() {
        assertThat(registry.upgradeTier("cheap")).isEqualTo("mid");
        assertThat(registry.upgradeTier("mid")).isEqualTo("powerful");
        assertThat(registry.upgradeTier("powerful")).isEqualTo("powerful");
    }
}
