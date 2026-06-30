# TASK.md — llm-router

## Goal
Build an intelligent LLM routing proxy that routes each request to the cheapest capable model and continuously validates routing quality.

---

## Active Tasks

### Phase 1 — Project Scaffold
| Status | Task | Notes |
|--------|------|-------|
| ✅ | Create Maven project with `pom.xml` | WebFlux, JPA, SQLite, Logstash, Lombok |
| ✅ | `RouterProperties.java` — `@ConfigurationProperties` | API keys, tier thresholds, quality gate, sidecar URL |
| ✅ | `application.yml` — bind all properties | Datasource → `./data/router.db` |
| ✅ | `LlmRouterApplication.java` | Entry point |

### Phase 2 — Data Models
| Status | Task | Notes |
|--------|------|-------|
| ✅ | Java records: `ChatMessage`, `ChatCompletionRequest`, `ChatCompletionResponse`, `RouterMeta`, `Usage` | OpenAI-compatible |
| ✅ | `RequestLog.java` — `@Entity` | Full routing metadata per request |
| ✅ | `RequestLogRepository.java` | `JpaRepository` + custom stats queries |

### Phase 3 — Provider Adapters
| Status | Task | Notes |
|--------|------|-------|
| ✅ | `LlmProvider.java` — interface | `Mono<ProviderResponse> complete()` |
| ✅ | `WebClientConfig.java` — one WebClient bean per provider | Auth headers, timeouts |
| ✅ | `OpenAiProvider.java` | WebClient → OpenAI `/v1/chat/completions` |
| ✅ | `AnthropicProvider.java` | WebClient → Anthropic `/v1/messages`; translate system role |
| ✅ | `OllamaProvider.java` | WebClient → Ollama OpenAI-compat endpoint |
| ✅ | `ProviderRegistry.java` | Tier map + fallback chain |

### Phase 4 — Classifier Sidecar (Python)
| Status | Task | Notes |
|--------|------|-------|
| ⚪ | `classifier-sidecar/features.py` | 11-feature vector extraction |
| ⚪ | `classifier-sidecar/scorer.py` | Heuristic + sklearn modes |
| ⚪ | `classifier-sidecar/main.py` | FastAPI `POST /score` |
| ⚪ | `classifier-sidecar/train.py` | Offline ML training script |
| ⚪ | `classifier-sidecar/Dockerfile` | python:3.11-slim, port 8001 |

### Phase 5 — Classifier Client (Java)
| Status | Task | Notes |
|--------|------|-------|
| ⚪ | `ClassifierClient.java` | WebClient → sidecar; Java heuristic fallback |

### Phase 6 — Evaluator
| Status | Task | Notes |
|--------|------|-------|
| ⚪ | `DeterministicScorer.java` | Length, refusal, finish_reason, latency |
| ⚪ | `LlmJudge.java` | claude-haiku-4-5 judge; only on quality failures |

### Phase 7 — Router Pipeline
| Status | Task | Notes |
|--------|------|-------|
| ⚪ | `RouterService.java` | Full reactive pipeline |
| ⚪ | `RouterController.java` | `POST /v1/chat/completions`, `/v1/models`, `/health` |
| ⚪ | `AdminController.java` | `/admin/stats`, `/admin/train-classifier` |

### Phase 8 — Structured Logging
| Status | Task | Notes |
|--------|------|-------|
| ⚪ | `logback-spring.xml` | JSONL rolling file + console dev profile |
| ⚪ | `StructuredLogger.java` | MDC-based per-request structured event |

### Phase 9 — Dashboard
| Status | Task | Notes |
|--------|------|-------|
| ⚪ | `dashboard/app.py` | 5 Streamlit tabs: Overview, Cost, Quality, Explorer, Classifier |
| ⚪ | `dashboard/requirements.txt` | streamlit, altair, pandas, sqlalchemy |
| ⚪ | `dashboard/Dockerfile` | python:3.11-slim, port 8501 |

### Phase 10 — Containerization
| Status | Task | Notes |
|--------|------|-------|
| ⚪ | `router/Dockerfile` | eclipse-temurin:21-jre-alpine, fat JAR |
| ⚪ | `docker-compose.yml` | router + classifier + ollama + dashboard |

### Phase 11 — Tests
| Status | Task | Notes |
|--------|------|-------|
| ⚪ | Unit tests: `DeterministicScorerTest`, `ProviderRegistryTest`, `ClassifierClientFallbackTest` | Mock WebClient |
| ⚪ | Integration: `RouterControllerTest` | `@SpringBootTest` + WebTestClient + WireMock + H2 |

---

## Completed

- ✅ Project scaffold (Phase 1): pom.xml, RouterProperties, application.yml, LlmRouterApplication
- ✅ Data models (Phase 2): ChatMessage, ChatCompletionRequest, ChatCompletionResponse, RouterMeta, Usage, RequestLog, RequestLogRepository
- ✅ Provider adapters (Phase 3): LlmProvider interface, WebClientConfig, OpenAiProvider, AnthropicProvider, OllamaProvider, ProviderRegistry with tier fallback chain
