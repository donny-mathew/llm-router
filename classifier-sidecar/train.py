"""
Offline training script. Run after accumulating labeled rows in SQLite.

Usage:
    python train.py --db ../data/router.db --out model/complexity_model.joblib

Label mapping:  cheap=0, mid=1, powerful=2
Rows need a 'tier_assigned' column with human-verified labels.
"""
import argparse
import os
import sqlite3
import json
import joblib
import numpy as np
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.model_selection import cross_val_score

from features import extract_features

TIER_LABELS = {"cheap": 0, "mid": 1, "powerful": 2}


def load_data(db_path: str):
    conn = sqlite3.connect(db_path)
    rows = conn.execute(
        "SELECT request_json, tier_assigned FROM request_logs WHERE tier_assigned IS NOT NULL"
    ).fetchall()
    conn.close()

    X, y = [], []
    for request_json, tier in rows:
        if tier not in TIER_LABELS:
            continue
        try:
            req = json.loads(request_json)
            features = extract_features(
                req.get("messages", []),
                req.get("max_tokens"),
                req.get("temperature"),
            )
            X.append(features)
            y.append(TIER_LABELS[tier])
        except Exception as e:
            print(f"Skipping row: {e}")

    return np.array(X), np.array(y)


def train(db_path: str, out_path: str):
    print(f"Loading data from {db_path}...")
    X, y = load_data(db_path)
    print(f"Loaded {len(X)} samples. Class distribution: {dict(zip(*np.unique(y, return_counts=True)))}")

    if len(X) < 30:
        print("Not enough samples (need ≥30). Aborting.")
        return

    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        ("clf", GradientBoostingClassifier(n_estimators=100, max_depth=4, random_state=42)),
    ])

    scores = cross_val_score(pipeline, X, y, cv=5, scoring="accuracy")
    print(f"CV accuracy: {scores.mean():.3f} ± {scores.std():.3f}")

    pipeline.fit(X, y)

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    joblib.dump(pipeline, out_path)
    print(f"Model saved to {out_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", default="../data/router.db")
    parser.add_argument("--out", default="model/complexity_model.joblib")
    args = parser.parse_args()
    train(args.db, args.out)
