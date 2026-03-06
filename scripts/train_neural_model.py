#!/usr/bin/env python3
"""
Train a small MLP for biblio-glutton pairwise matching and export to ONNX.

Architecture: input(8) -> hidden(32) -> hidden(16) -> output(1) + sigmoid

Usage:
    python scripts/train_neural_model.py \
        --input data/training/matching_training.csv \
        --output data/models/neural_matching.onnx

Requirements:
    pip install torch scikit-learn pandas numpy onnx onnxruntime
"""

import argparse
import os

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, TensorDataset
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report

FEATURE_NAMES = [
    "titleSim", "authorSim", "blockingScore", "jtitleSim",
    "yearMatch", "volumeMatch", "issueMatch", "firstPageMatch"
]

NUM_FEATURES = len(FEATURE_NAMES)


class MatchingMLP(nn.Module):
    def __init__(self):
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(NUM_FEATURES, 32),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(32, 16),
            nn.ReLU(),
            nn.Dropout(0.1),
            nn.Linear(16, 1),
            nn.Sigmoid(),
        )

    def forward(self, x):
        return self.net(x)


def main():
    parser = argparse.ArgumentParser(description="Train neural MLP for pairwise matching")
    parser.add_argument("--input", required=True, help="Path to training CSV")
    parser.add_argument("--output", default="data/models/neural_matching.onnx",
                        help="Output ONNX model file")
    parser.add_argument("--test-size", type=float, default=0.2, help="Test split ratio")
    parser.add_argument("--random-state", type=int, default=42, help="Random seed")
    parser.add_argument("--epochs", type=int, default=50, help="Training epochs")
    parser.add_argument("--batch-size", type=int, default=256, help="Batch size")
    parser.add_argument("--lr", type=float, default=0.001, help="Learning rate")
    args = parser.parse_args()

    torch.manual_seed(args.random_state)
    np.random.seed(args.random_state)

    # Load data
    print(f"Loading training data from {args.input}...")
    df = pd.read_csv(args.input)
    print(f"Loaded {len(df)} samples ({df['label'].sum()} positive, {(1 - df['label']).sum():.0f} negative)")

    # Handle missing values - fill with 0
    X = df[FEATURE_NAMES].fillna(0.0).values.astype(np.float32)
    y = df["label"].values.astype(np.float32)

    # Split
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=args.test_size, random_state=args.random_state, stratify=y
    )

    # Convert to tensors
    X_train_t = torch.from_numpy(X_train)
    y_train_t = torch.from_numpy(y_train).unsqueeze(1)
    X_test_t = torch.from_numpy(X_test)
    y_test_t = torch.from_numpy(y_test).unsqueeze(1)

    train_dataset = TensorDataset(X_train_t, y_train_t)
    train_loader = DataLoader(train_dataset, batch_size=args.batch_size, shuffle=True)

    # Build model
    model = MatchingMLP()
    criterion = nn.BCELoss()
    optimizer = optim.Adam(model.parameters(), lr=args.lr)

    # Train
    print(f"Training MLP for {args.epochs} epochs...")
    for epoch in range(args.epochs):
        model.train()
        total_loss = 0
        for batch_X, batch_y in train_loader:
            optimizer.zero_grad()
            output = model(batch_X)
            loss = criterion(output, batch_y)
            loss.backward()
            optimizer.step()
            total_loss += loss.item()

        if (epoch + 1) % 10 == 0:
            model.eval()
            with torch.no_grad():
                test_output = model(X_test_t)
                test_loss = criterion(test_output, y_test_t).item()
            print(f"  Epoch {epoch + 1}/{args.epochs}: train_loss={total_loss / len(train_loader):.4f}, test_loss={test_loss:.4f}")

    # Evaluate
    model.eval()
    with torch.no_grad():
        y_pred_prob = model(X_test_t).numpy().flatten()
        y_pred = (y_pred_prob >= 0.5).astype(int)

    print("\nTest set evaluation:")
    print(classification_report(y_test, y_pred, target_names=["non-match", "match"]))

    # Export to ONNX
    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    dummy_input = torch.randn(1, NUM_FEATURES)
    torch.onnx.export(
        model, dummy_input, args.output,
        input_names=["features"],
        output_names=["score"],
        dynamic_axes={"features": {0: "batch_size"}, "score": {0: "batch_size"}},
        opset_version=11,
    )
    print(f"\nONNX model saved to {args.output}")


if __name__ == "__main__":
    main()
