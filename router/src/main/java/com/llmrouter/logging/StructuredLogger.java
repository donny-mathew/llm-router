package com.llmrouter.logging;

import com.llmrouter.model.RouterMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Emits one structured log event per routed request with all routing
 * decision fields as MDC keys — picked up by LogstashEncoder as top-level
 * JSON fields in the JSONL output.
 */
@Component
public class StructuredLogger {

    private static final Logger log = LoggerFactory.getLogger(StructuredLogger.class);

    public void logRequest(String requestId, RouterMeta meta) {
        String id = requestId != null ? requestId : UUID.randomUUID().toString();
        try {
            MDC.put("request_id",       id);
            MDC.put("tier",             meta.tierAssigned());
            MDC.put("provider",         meta.providerUsed());
            MDC.put("model",            meta.modelUsed());
            MDC.put("complexity_score", String.valueOf(meta.complexityScore()));
            MDC.put("quality_score",    meta.qualityScore() != null ? String.valueOf(meta.qualityScore()) : "");
            MDC.put("latency_ms",       String.valueOf(meta.latencyMs()));
            MDC.put("cost_usd",         String.valueOf(meta.estimatedCostUsd()));
            MDC.put("rerouted",         String.valueOf(meta.rerouted()));

            log.info("request_routed");
        } finally {
            MDC.clear();
        }
    }
}
