package com.llmrouter.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmrouter.classifier.ClassifierClient;
import com.llmrouter.config.RouterProperties;
import com.llmrouter.evaluator.DeterministicScorer;
import com.llmrouter.evaluator.LlmJudge;
import com.llmrouter.logging.StructuredLogger;
import com.llmrouter.provider.LlmProvider;
import com.llmrouter.provider.ProviderRegistry;
import com.llmrouter.provider.ProviderResponse;
import com.llmrouter.storage.RequestLog;
import com.llmrouter.storage.RequestLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static com.llmrouter.TestData.request;
import static com.llmrouter.TestData.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RouterServiceTest {

    private final ClassifierClient classifier = mock(ClassifierClient.class);
    private final ProviderRegistry registry = mock(ProviderRegistry.class);
    private final DeterministicScorer scorer = mock(DeterministicScorer.class);
    private final LlmJudge judge = mock(LlmJudge.class);
    private final RequestLogRepository repo = mock(RequestLogRepository.class);
    private final StructuredLogger structuredLogger = mock(StructuredLogger.class);

    private final RouterProperties props = new RouterProperties(
            null,
            new RouterProperties.Tiers(0.35, 0.70),
            new RouterProperties.QualityGate(0.65, true, 1),
            null);

    private final RouterService service = new RouterService(
            classifier, registry, scorer, judge, repo, structuredLogger, new ObjectMapper(), props);

    private static LlmProvider provider(String name, String model, double costPer1k) {
        LlmProvider p = mock(LlmProvider.class);
        when(p.getName()).thenReturn(name);
        when(p.getModelId()).thenReturn(model);
        when(p.estimateCost(anyInt(), anyInt())).thenAnswer(inv ->
                (inv.<Integer>getArgument(0) + inv.<Integer>getArgument(1)) / 1000.0 * costPer1k);
        return p;
    }

    @Test
    void metaAndLogReflectServingProviderAndRealLatency() {
        LlmProvider haiku = provider("anthropic", "claude-haiku-4-5", 0.001);
        when(classifier.score(any())).thenReturn(Mono.just(new ClassifierClient.ClassifierResponse(0.5, "mid")));
        when(registry.complete(eq("mid"), any()))
                .thenReturn(Mono.just(new ProviderResponse(response("answer", "stop"), 1234, haiku)));
        when(scorer.score(any(), any(), anyLong())).thenReturn(0.9);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StepVerifier.create(service.route(request("question")))
                .assertNext(r -> {
                    assertThat(r.xRouterMeta().modelUsed()).isEqualTo("claude-haiku-4-5");
                    assertThat(r.xRouterMeta().latencyMs()).isEqualTo(1234);
                    assertThat(r.xRouterMeta().estimatedCostUsd()).isEqualTo(0.002);
                    assertThat(r.xRouterMeta().rerouted()).isFalse();
                })
                .verifyComplete();

        ArgumentCaptor<RequestLog> saved = ArgumentCaptor.forClass(RequestLog.class);
        verify(repo, timeout(2000)).save(saved.capture());
        assertThat(saved.getValue().getLatencyMs()).isEqualTo(1234);
        assertThat(saved.getValue().getModelUsed()).isEqualTo("claude-haiku-4-5");
    }

    @Test
    void lowQualityResponseFailingJudgeIsReroutedToNextTierOnce() {
        LlmProvider cheap = provider("ollama", "llama3.2:3b", 0);
        LlmProvider mid = provider("anthropic", "claude-haiku-4-5", 0.001);
        when(classifier.score(any())).thenReturn(Mono.just(new ClassifierClient.ClassifierResponse(0.1, "cheap")));
        when(registry.complete(eq("cheap"), any()))
                .thenReturn(Mono.just(new ProviderResponse(response("meh", "length"), 10, cheap)));
        when(registry.complete(eq("mid"), any()))
                .thenReturn(Mono.just(new ProviderResponse(response("meh again", "length"), 20, mid)));
        when(registry.upgradeTier("cheap")).thenReturn("mid");
        when(scorer.score(any(), any(), anyLong())).thenReturn(0.2);
        when(judge.evaluate(any(), any()))
                .thenReturn(Mono.just(new LlmJudge.JudgeVerdict(1, 1, 1, 1, 1, "fail", "too short")));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StepVerifier.create(service.route(request("explain monads")))
                .assertNext(r -> {
                    assertThat(r.xRouterMeta().tierAssigned()).isEqualTo("mid");
                    assertThat(r.xRouterMeta().rerouted()).isTrue();
                })
                .verifyComplete();

        // Only one judge call and one upgrade: the rerouted attempt must not reroute again
        verify(judge, times(1)).evaluate(any(), any());
        verify(registry, never()).complete(eq("powerful"), any());
        verify(repo, timeout(2000).times(2)).save(any());
    }
}
