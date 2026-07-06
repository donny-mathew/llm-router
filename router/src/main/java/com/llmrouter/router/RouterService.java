package com.llmrouter.router;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmrouter.classifier.ClassifierClient;
import com.llmrouter.config.RouterProperties;
import com.llmrouter.evaluator.DeterministicScorer;
import com.llmrouter.evaluator.LlmJudge;
import com.llmrouter.logging.StructuredLogger;
import com.llmrouter.model.ChatCompletionRequest;
import com.llmrouter.model.ChatCompletionResponse;
import com.llmrouter.model.RouterMeta;
import com.llmrouter.provider.LlmProvider;
import com.llmrouter.provider.ProviderRegistry;
import com.llmrouter.storage.RequestLog;
import com.llmrouter.storage.RequestLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;

@Service
public class RouterService {

    private static final Logger log = LoggerFactory.getLogger(RouterService.class);

    private final ClassifierClient classifierClient;
    private final ProviderRegistry providerRegistry;
    private final DeterministicScorer deterministicScorer;
    private final LlmJudge llmJudge;
    private final RequestLogRepository logRepository;
    private final StructuredLogger structuredLogger;
    private final ObjectMapper objectMapper;
    private final RouterProperties props;

    public RouterService(
            ClassifierClient classifierClient,
            ProviderRegistry providerRegistry,
            DeterministicScorer deterministicScorer,
            LlmJudge llmJudge,
            RequestLogRepository logRepository,
            StructuredLogger structuredLogger,
            ObjectMapper objectMapper,
            RouterProperties props) {
        this.classifierClient = classifierClient;
        this.providerRegistry = providerRegistry;
        this.deterministicScorer = deterministicScorer;
        this.llmJudge = llmJudge;
        this.logRepository = logRepository;
        this.structuredLogger = structuredLogger;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    public Mono<ChatCompletionResponse> route(ChatCompletionRequest request) {
        return classifierClient.score(request)
                .flatMap(classification -> executeWithTier(
                        request,
                        classification.tier(),
                        classification.score(),
                        false,
                        null));
    }

    private Mono<ChatCompletionResponse> executeWithTier(
            ChatCompletionRequest request,
            String tier,
            double complexityScore,
            boolean alreadyRerouted,
            String rerouteReason) {

        return providerRegistry.complete(tier, request)
                .flatMap(providerResponse -> {
                    LlmProvider provider = providerRegistry.getFirstProvider(tier);
                    double qualityScore = deterministicScorer.score(
                            request, providerResponse.response(), providerResponse.latencyMs());

                    boolean belowThreshold = qualityScore < props.qualityGate().threshold();
                    boolean canReroute = props.qualityGate().enableReroute() && !alreadyRerouted;

                    if (belowThreshold && canReroute) {
                        return llmJudge.evaluate(request, providerResponse.response())
                                .flatMap(verdict -> {
                                    if (verdict.isFail()) {
                                        String upgradedTier = providerRegistry.upgradeTier(tier);
                                        log.info("Rerouting {} → {} (quality={}, reason={})",
                                                tier, upgradedTier, qualityScore, verdict.reason());
                                        return executeWithTier(
                                                request, upgradedTier, complexityScore,
                                                true, verdict.reason())
                                                .flatMap(finalResponse -> {
                                                    // Save the original (failed) attempt log with reroute flag
                                                    saveLog(request, providerResponse.response(),
                                                            provider, tier, complexityScore,
                                                            qualityScore, true, verdict.reason(),
                                                            toJson(verdict));
                                                    return Mono.just(finalResponse);
                                                });
                                    }
                                    // Judge says pass despite low deterministic score
                                    return buildAndLog(request, providerResponse, provider, tier,
                                            complexityScore, qualityScore, alreadyRerouted, rerouteReason, null);
                                });
                    }

                    return buildAndLog(request, providerResponse, provider, tier,
                            complexityScore, qualityScore, alreadyRerouted, rerouteReason, null);
                });
    }

    private Mono<ChatCompletionResponse> buildAndLog(
            ChatCompletionRequest request,
            com.llmrouter.provider.ProviderResponse providerResponse,
            LlmProvider provider,
            String tier,
            double complexityScore,
            double qualityScore,
            boolean rerouted,
            String rerouteReason,
            String judgeVerdict) {

        double cost = provider.estimateCost(
                providerResponse.response().usage() != null ? providerResponse.response().usage().promptTokens() : 0,
                providerResponse.response().usage() != null ? providerResponse.response().usage().completionTokens() : 0);

        RouterMeta meta = new RouterMeta(
                complexityScore,
                tier,
                provider.getName(),
                provider.getModelId(),
                rerouted,
                qualityScore,
                providerResponse.latencyMs(),
                cost);

        ChatCompletionResponse finalResponse = new ChatCompletionResponse(
                providerResponse.response().id(),
                providerResponse.response().object(),
                providerResponse.response().created(),
                providerResponse.response().model(),
                providerResponse.response().choices(),
                providerResponse.response().usage(),
                meta);

        saveLog(request, providerResponse.response(), provider, tier, complexityScore,
                qualityScore, rerouted, rerouteReason, judgeVerdict);
        structuredLogger.logRequest(providerResponse.response().id(), meta);

        return Mono.just(finalResponse);
    }

    private void saveLog(
            ChatCompletionRequest request,
            ChatCompletionResponse response,
            LlmProvider provider,
            String tier,
            double complexityScore,
            double qualityScore,
            boolean rerouted,
            String rerouteReason,
            String judgeVerdict) {

        double cost = provider.estimateCost(
                response.usage() != null ? response.usage().promptTokens() : 0,
                response.usage() != null ? response.usage().completionTokens() : 0);

        RequestLog entry = RequestLog.builder()
                .createdAt(Instant.now())
                .requestJson(toJson(request))
                .modelRequested(request.model())
                .complexityScore(complexityScore)
                .tierAssigned(tier)
                .providerUsed(provider.getName())
                .modelUsed(provider.getModelId())
                .responseJson(toJson(response))
                .promptTokens(response.usage() != null ? response.usage().promptTokens() : 0)
                .completionTokens(response.usage() != null ? response.usage().completionTokens() : 0)
                .latencyMs(0) // latency captured in ProviderResponse; passed via buildAndLog in full impl
                .estimatedCostUsd(cost)
                .qualityScore(qualityScore)
                .rerouted(rerouted)
                .rerouteReason(rerouteReason)
                .judgeVerdict(judgeVerdict)
                .build();

        Mono.fromCallable(() -> logRepository.save(entry))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        saved -> log.debug("Logged request {}", saved.getId()),
                        err   -> log.error("Failed to save request log: {}", err.getMessage()));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
