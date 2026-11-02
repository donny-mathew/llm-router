# llm-router

[![CI](https://github.com/donny-mathew/llm-router/actions/workflows/ci.yml/badge.svg)](https://github.com/donny-mathew/llm-router/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F)

An OpenAI-compatible proxy that sends each chat request to the **cheapest model likely to handle it**, checks the answer's quality, and escalates to a stronger model only when the answer falls short.

Point any OpenAI SDK at `http://localhost:8000/v1` and the router picks the model for you.

## How a request flows

```
POST /v1/chat/completions
        │
        ▼
 ┌──────────────┐   timeout / down   ┌────────────────────┐
 │  Classifier  │ ─────────────────▶ │ Java heuristic     │
 │  sidecar     │                    │ fallback (in-proc) │
 └──────┬───────┘                    └─────────┬──────────┘
        │ complexity score 0..1 → tier         │
        ▼                                      ▼
   cheap ≤ 0.35        mid ≤ 0.70         powerful
   llama3.2:3b     llama3.1:8b → haiku   sonnet → gpt-4o      (fallback chain per tier)
        │
        ▼
 Deterministic quality score (length, refusal, finish_reason, latency): no LLM call
        │
        ├── ≥ 0.65 ─────────────▶ return response
        └── < 0.65 → LLM judge (Haiku)
                      ├── pass → return response
                      └── fail → re-run once on the next tier up
```

Every response carries an `x_router_meta` block showing the tier, the model that actually served it, the quality score, latency, and estimated cost. Every request is logged to SQLite for `/admin/stats`.

## Design decisions

- **Reactive end to end** (WebFlux + WebClient). Provider calls, classifier calls and log writes never block a request thread; JPA writes are offloaded to `boundedElastic`.
- **The classifier can't take routing down.** If the Python sidecar times out (3s) or is unreachable, a Java port of the same weighted heuristic takes over.
- **The quality gate is cheap first.** The deterministic scorer runs on every response at zero cost; the LLM judge runs only on suspected failures, and a reroute happens at most once per request.
- **Per-tier fallback chains.** If a provider errors, the next provider in that tier serves the request, and the response metadata credits the provider that actually answered.
- **Learns from its own traffic.** `train.py` fits a scikit-learn model on the logged requests; the sidecar switches from heuristic to ML mode once a model file exists.

## Tech stack

| Layer | Choice |
|---|---|
| Router | Java 21, Spring Boot 3.3, WebFlux, Spring Data JPA |
| Storage | SQLite (Postgres swap = driver + `application.yml` only) |
| Classifier | Python 3.11, FastAPI, scikit-learn |
| Logging | Logback + logstash encoder (JSONL) |
| Tests | JUnit 5, Mockito, Reactor `StepVerifier` |

## Running locally

**Prerequisites:** JDK 21+, Maven, Python 3.11, and either [Ollama](https://ollama.com) (`ollama pull llama3.2:3b && ollama pull llama3.1:8b`) or API keys for Anthropic/OpenAI.

```bash
# 1. Classifier sidecar (optional: the router falls back to its heuristic without it)
cd classifier-sidecar
pip install -r requirements.txt
uvicorn main:app --port 8001

# 2. Router
cd router
export ANTHROPIC_API_KEY=... OPENAI_API_KEY=...   # see .env.example
mvn spring-boot:run
```

```bash
curl -s localhost:8000/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"auto","messages":[{"role":"user","content":"What is 2+2?"}]}' | jq .x_router_meta
```

## API

| Endpoint | Purpose |
|---|---|
| `POST /v1/chat/completions` | OpenAI-compatible chat completion (non-streaming) |
| `GET /v1/models` | Models the router can dispatch to, with their tier |
| `GET /health` | Liveness |
| `GET /admin/stats` | Request counts, reroute count, cost and quality by tier/provider |
| `POST /admin/train-classifier` | Retrain the complexity model from logged traffic |

## Tests

```bash
cd router && mvn test
```

## Roadmap

- [ ] `docker-compose.yml` for router + classifier + Ollama
- [ ] Streamlit dashboard (cost, quality, classifier accuracy)
- [ ] Streaming (`stream: true`) pass-through
- [ ] Auth on `/admin/*`
- [ ] Integration tests with WireMock-stubbed providers
