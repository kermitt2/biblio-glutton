#!/usr/bin/env python3
"""
Train an XGBoost model for biblio-glutton pairwise matching.

Usage:
    python scripts/train_xgboost_model.py \
        --input data/training/matching_training.csv \
        --output data/models/xgboost_matching.model

Requirements:
    pip install xgboost scikit-learn pandas numpy
"""

import argparse
import os

import numpy as np
import pandas as pd
import xgboost as xgb
from sklearn.model_selection import train_test_split, GridSearchCV
from sklearn.metrics import classification_report, precision_recall_fscore_support

FEATURE_NAMES = [
    "titleSim", "authorSim", "blockingScore", "jtitleSim",
    "yearMatch", "volumeMatch", "issueMatch", "firstPageMatch"
]


def main():
    parser = argparse.ArgumentParser(description="Train XGBoost for pairwise matching")
    parser.add_argument("--input", required=True, help="Path to training CSV")
    parser.add_argument("--output", default="data/models/xgboost_matching.model",
                        help="Output model file")
    parser.add_argument("--test-size", type=float, default=0.2, help="Test split ratio")
    parser.add_argument("--random-state", type=int, default=42, help="Random seed")
    parser.add_argument("--tune", action="store_true", help="Enable hyperparameter tuning (slower)")
    args = parser.parse_args()

    # Load data
    print(f"Loading training data from {args.input}...")
    df = pd.read_csv(args.input)
    print(f"Loaded {len(df)} samples ({df['label'].sum()} positive, {(1 - df['label']).sum():.0f} negative)")

    # Keep NaN for missing values - XGBoost handles them natively
    X = df[FEATURE_NAMES].values
    y = df["label"].values

    # Split
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=args.test_size, random_state=args.random_state, stratify=y
    )

    if args.tune:
        print("Running hyperparameter tuning...")
        param_grid = {
            "max_depth": [3, 5, 7],
            "learning_rate": [0.01, 0.1, 0.3],
            "n_estimators": [50, 100, 200],
            "min_child_weight": [1, 3, 5],
        }
        base_model = xgb.XGBClassifier(
            objective="binary:logistic",
            eval_metric="logloss",
            random_state=args.random_state,
            use_label_encoder=False,
        )
        grid = GridSearchCV(base_model, param_grid, cv=3, scoring="f1", verbose=1, n_jobs=-1)
        grid.fit(X_train, y_train)
        print(f"Best params: {grid.best_params_}")
        model = grid.best_estimator_
    else:
        print("Training XGBoost with default params...")
        model = xgb.XGBClassifier(
            objective="binary:logistic",
            eval_metric="logloss",
            max_depth=5,
            learning_rate=0.1,
            n_estimators=100,
            min_child_weight=3,
            random_state=args.random_state,
            use_label_encoder=False,
        )
        model.fit(
            X_train, y_train,
            eval_set=[(X_test, y_test)],
            verbose=False,
        )

    # Evaluate
    y_pred = model.predict(X_test)
    print("\nTest set evaluation:")
    print(classification_report(y_test, y_pred, target_names=["non-match", "match"]))

    # Feature importance
    print("Feature importance (gain):")
    importance = model.feature_importances_
    for name, imp in sorted(zip(FEATURE_NAMES, importance), key=lambda x: x[1], reverse=True):
        print(f"  {name:20s}: {imp:.4f}")

    # Save model
    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    model.save_model(args.output)
    print(f"\nModel saved to {args.output}")


if __name__ == "__main__":
    main()
