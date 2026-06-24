# llm-router

## Project Overview
An intelligent routing layer that sits in front of multiple LLM providers (OpenAI, Anthropic, Ollama), analyzes each request's complexity, routes it to the cheapest capable model, and continuously validates response quality.

## Tech Stack
- **Language**: Java 21
- **Framework**: Spring Boot 3.3 + WebFlux (reactive)
- **HTTP Client**: Spring WebClient
- **Persistence**: Spring Data JPA + SQLite (`sqlite-jdbc`)
- **Logging**: Logback + `logstash-logback-encoder` (JSONL)
- **Classifier Sidecar**: Python 3.11 + FastAPI + scikit-learn (port 8001)
- **Dashboard**: Streamlit + Altair (port 8501)
- **Containers**: Docker + docker-compose

## Project Structure
```
router/          Spring Boot Maven app
classifier-sidecar/  Python FastAPI complexity scorer
dashboard/       Streamlit visualization app
```

## Key Conventions
- Constructor injection everywhere — no @Autowired field injection
- All provider calls, classifier calls, and DB writes are non-blocking (Mono/Flux)
- `@JsonIgnoreProperties(ignoreUnknown=true)` on request models for OpenAI pass-through compatibility
- RouterMeta is injected into every response as `x_router_meta`
- Classifier sidecar failure → fallback to local Java heuristic (never blocks routing)

## Environment Setup
1. Copy `.env.example` → `.env`, fill in API keys
2. `docker-compose up --build`
3. Router: http://localhost:8000
4. Classifier: http://localhost:8001
5. Dashboard: http://localhost:8501

## Notes for Claude
- Always check TASK.md before starting work
- Ask for approval before starting each phase
- Prefer Spring Boot 3.x idioms (records, virtual threads where applicable)
- SQLite → PostgreSQL swap is isolated to `application.yml` + `pom.xml` driver only
