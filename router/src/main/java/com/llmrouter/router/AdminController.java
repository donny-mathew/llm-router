package com.llmrouter.router;

import com.llmrouter.storage.RequestLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final RequestLogRepository logRepository;

    public AdminController(RequestLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @GetMapping("/stats")
    public Mono<Map<String, Object>> stats() {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();

            result.put("total_requests", logRepository.count());
            result.put("total_rerouted", logRepository.countRerouted());
            result.put("avg_latency_ms", logRepository.avgLatencyMs());

            List<Map<String, Object>> byTier = logRepository.statsByTier().stream()
                    .map(row -> Map.<String, Object>of(
                            "tier",            row[0],
                            "count",           row[1],
                            "avg_cost_usd",    row[2],
                            "avg_quality",     row[3]))
                    .toList();
            result.put("by_tier", byTier);

            List<Map<String, Object>> byCost = logRepository.costByProvider().stream()
                    .map(row -> Map.<String, Object>of(
                            "provider",        row[0],
                            "total_cost_usd",  row[1]))
                    .toList();
            result.put("cost_by_provider", byCost);

            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/train-classifier")
    public Mono<ResponseEntity<Map<String, String>>> trainClassifier() {
        return Mono.fromCallable(() -> {
            ProcessBuilder pb = new ProcessBuilder(
                    "python", "train.py",
                    "--db", "../data/router.db",
                    "--out", "model/complexity_model.joblib")
                    .directory(new java.io.File("../classifier-sidecar"))
                    .redirectErrorStream(true);
            Process process = pb.start();
            int exit = process.waitFor();
            if (exit == 0) {
                log.info("Classifier training completed successfully");
                return ResponseEntity.ok(Map.of("status", "ok", "message", "Training completed"));
            } else {
                log.warn("Classifier training exited with code {}", exit);
                return ResponseEntity.<Map<String, String>>internalServerError()
                        .body(Map.of("status", "error", "message", "Training failed with exit code " + exit));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
