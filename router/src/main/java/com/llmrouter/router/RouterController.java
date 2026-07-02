package com.llmrouter.router;

import com.llmrouter.model.ChatCompletionRequest;
import com.llmrouter.model.ChatCompletionResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
public class RouterController {

    private final RouterService routerService;

    public RouterController(RouterService routerService) {
        this.routerService = routerService;
    }

    @PostMapping(
            value = "/v1/chat/completions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ChatCompletionResponse> chatCompletions(@RequestBody ChatCompletionRequest request) {
        return routerService.route(request);
    }

    @GetMapping("/v1/models")
    public Mono<Map<String, Object>> listModels() {
        return Mono.just(Map.of(
                "object", "list",
                "data", List.of(
                        modelEntry("llama3.2:3b",        "cheap"),
                        modelEntry("llama3.1:8b",        "mid"),
                        modelEntry("claude-haiku-4-5",   "mid"),
                        modelEntry("claude-sonnet-4-6",  "powerful"),
                        modelEntry("gpt-4o",             "powerful")
                )
        ));
    }

    @GetMapping("/health")
    public Mono<Map<String, String>> health() {
        return Mono.just(Map.of("status", "ok"));
    }

    private Map<String, Object> modelEntry(String id, String tier) {
        return Map.of("id", id, "object", "model", "tier", tier);
    }
}
