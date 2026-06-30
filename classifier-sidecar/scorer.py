import os
import joblib
import numpy as np
from features import extract_features

MODEL_PATH = os.environ.get("MODEL_PATH", "model/complexity_model.joblib")

HEURISTIC_WEIGHTS = {
    "code_block_count_norm":      0.20,
    "instruction_depth_norm":     0.25,
    "multi_step_indicators_norm": 0.15,
    "total_chars_norm":           0.15,
    "math_symbols_norm":          0.15,
    "message_count_norm":         0.10,
}

FEATURE_NAMES = [
    "total_chars_norm", "message_count_norm", "avg_message_length_norm",
    "system_prompt_present", "code_block_count_norm", "question_count_norm",
    "instruction_depth_norm", "multi_step_indicators_norm", "math_symbols_norm",
    "max_tokens_norm", "temperature",
]


def _load_model():
    if os.path.exists(MODEL_PATH):
        return joblib.load(MODEL_PATH)
    return None


_model = _load_model()


def _heuristic_score(features: list[float]) -> float:
    feat = dict(zip(FEATURE_NAMES, features))
    score = sum(feat.get(k, 0.0) * w for k, w in HEURISTIC_WEIGHTS.items())
    return min(1.0, score)


def score(messages: list[dict], max_tokens: int | None, temperature: float | None) -> float:
    features = extract_features(messages, max_tokens, temperature)
    if _model is not None:
        # Class order: cheap=0, mid=1, powerful=2 — use P(powerful) as score
        proba = _model.predict_proba([features])[0]
        return float(proba[2] * 1.0 + proba[1] * 0.5)
    return _heuristic_score(features)


def assign_tier(complexity_score: float, cheap_max: float = 0.35, mid_max: float = 0.70) -> str:
    if complexity_score <= cheap_max:
        return "cheap"
    if complexity_score <= mid_max:
        return "mid"
    return "powerful"
