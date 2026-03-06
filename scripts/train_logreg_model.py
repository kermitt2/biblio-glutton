#!/usr/bin/env python3
"""
Train a logistic regression model for biblio-glutton pairwise matching.

Usage:
    python scripts/train_logreg_model.py \
        --input data/training/matching_training.csv \
        --output data/models/logreg_weights.json

Requirements:
    pip install scikit-learn pandas numpy
"""

import argparse
import json
import os

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, precision_recall_fscore_support

FEATURE_NAMES = [
    "titleSim", "authorSim", "blockingScore", "jtitleSim",
    "yearMatch", "volumeMatch", "issueMatch", "firstPageMatch"
]


def main():
    parser = argparse.ArgumentParser(description="Train logistic regression for pairwise matching")
    parser.add_argument("--input", required=True, help="Path to training CSV")
    parser.add_argument("--output", default="data/models/logreg_weights.json",
                        help="Output JSON weights file")
    parser.add_argument("--test-size", type=float, default=0.2, help="Test split ratio")
    parser.add_argument("--random-state", type=int, default=42, help="Random seed")
    args = parser.parse_args()

    # Load data
    print(f"Loading training data from {args.input}...")
    df = pd.read_csv(args.input)
    print(f"Loaded {len(df)} samples ({df['label'].sum()} positive, {(1 - df['label']).sum():.0f} negative)")

    # Handle missing values (empty cells become NaN) - fill with 0
    X = df[FEATURE_NAMES].fillna(0.0).values
    y = df["label"].values

    # Split
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=args.test_size, random_state=args.random_state, stratify=y
    )

    # Train
    print("Training logistic regression...")
    model = LogisticRegression(
        max_iter=1000,
        C=1.0,
        solver="lbfgs",
        random_state=args.random_state
    )
    model.fit(X_train, y_train)

    # Evaluate
    y_pred = model.predict(X_test)
    print("\nTest set evaluation:")
    print(classification_report(y_test, y_pred, target_names=["non-match", "match"]))

    # Feature importance
    print("Feature weights:")
    weights = model.coef_[0]
    for name, weight in sorted(zip(FEATURE_NAMES, weights), key=lambda x: abs(x[1]), reverse=True):
        print(f"  {name:20s}: {weight:+.4f}")
    print(f"  {'bias':20s}: {model.intercept_[0]:+.4f}")

    # Export weights
    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    weights_dict = {
        "bias": float(model.intercept_[0]),
        "weights": [float(w) for w in weights],
        "feature_names": FEATURE_NAMES
    }
    with open(args.output, "w") as f:
        json.dump(weights_dict, f, indent=2)
    print(f"\nWeights saved to {args.output}")


if __name__ == "__main__":
    main()
