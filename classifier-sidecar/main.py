from fastapi import FastAPI
from pydantic import BaseModel
from typing import Any
import scorer

app = FastAPI(title="LLM Router Classifier", version="0.1.0")


class ScoreRequest(BaseModel):
    messages: list[dict[str, Any]]
    max_tokens: int | None = None
    temperature: float | None = None
    cheap_max: float = 0.35
    mid_max: float = 0.70


class ScoreResponse(BaseModel):
    score: float
    tier: str


@app.post("/score", response_model=ScoreResponse)
def score_request(req: ScoreRequest) -> ScoreResponse:
    complexity_score = scorer.score(req.messages, req.max_tokens, req.temperature)
    tier = scorer.assign_tier(complexity_score, req.cheap_max, req.mid_max)
    return ScoreResponse(score=complexity_score, tier=tier)


@app.get("/health")
def health():
    return {"status": "ok", "model_loaded": scorer._model is not None}
