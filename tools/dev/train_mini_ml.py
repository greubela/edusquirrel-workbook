#!/usr/bin/env python3
"""Train a tiny multiclass softmax regression model from JSONL logs.

Input format: JSON Lines, one object per line, e.g. emitted by MlTrainingLogger:
  {
    "timestampEpochMillis": 123,
    "exerciseId": "...",
    "submissionNr": 1,
    "weakLabel": "COMPILE_ERROR",
    "weakConfidence": 0.82,
    "features": {"print_count": 3, ...}
  }

Output format: JSON compatible with Scala.js SoftmaxModel.fromJson:
  {
    "labels": [...],
    "feature_index": {"feat": 0, ...},
    "weights": [[...], ...],
    "bias": [...],
    "standardize": {"mean": [...], "std": [...]}
  }

This is intentionally dependency-free (no numpy/sklearn) to keep setup simple.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import random
from typing import Any, Dict, List, Tuple

DEFAULT_FEATURE_ORDER: List[str] = [
    "lines_of_code",
    "non_empty_lines",
    "blank_lines",
    "comment_lines",
    "print_count",
    "input_call_count",
    "random_call_count",
    "has_pass_statement",
    "boundary_hint_score",
    "stdout_line_count",
    "stderr_line_count",
    "tests_total",
    "tests_passed",
    "tests_failed",
    "has_runtime_error",
    "py_rules_failed_warning",
    "py_rules_failed_error",
    "vm_rules_failed_warning",
    "vm_rules_failed_error",
    # error-type lexical flags (added in FeatureExtractor.scala)
    "err_has_traceback",
    "err_syntaxerror",
    "err_indentationerror",
    "err_nameerror",
    "err_typeerror",
    "err_valueerror",
    "err_attributeerror",
    "err_indexerror",
    "err_keyerror",
    "err_zerodivisionerror",
    "err_timeout",
]


def _feature_hash(ex: Dict[str, Any]) -> str:
    feats = ex.get("features")
    if not isinstance(feats, dict):
        return "<no-features>"
    items = []
    for k in sorted(feats.keys()):
        if not isinstance(k, str):
            continue
        v = feats.get(k)
        try:
            fv = float(v)
        except Exception:
            fv = 0.0
        # round to reduce float formatting noise
        fv = round(fv, 6)
        items.append(f"{k}={fv}")
    return str(hash("|".join(items)))


def _exercise_key(ex: Dict[str, Any]) -> str:
    v = ex.get("exerciseId")
    return str(v) if v is not None else "<none>"


def _split_grouped(
    examples: List[Dict[str, Any]],
    group_key_fn,
    test_split: float,
    seed: int,
) -> Tuple[List[int], List[int]]:
    # Group indices by key
    groups: Dict[str, List[int]] = {}
    for i, ex in enumerate(examples):
        k = str(group_key_fn(ex))
        groups.setdefault(k, []).append(i)

    keys = list(groups.keys())
    rnd = random.Random(seed)
    rnd.shuffle(keys)

    # Fill test groups until reaching target test size
    target_test = int(round(test_split * len(examples)))
    test_idx: List[int] = []
    train_idx: List[int] = []
    test_count = 0
    for k in keys:
        idxs = groups[k]
        if test_count < target_test:
            test_idx.extend(idxs)
            test_count += len(idxs)
        else:
            train_idx.extend(idxs)

    return train_idx, test_idx


def _stable_softmax(logits: List[float]) -> List[float]:
    m = max(logits)
    exps = [math.exp(z - m) for z in logits]
    s = sum(exps)
    if s == 0.0:
        return [0.0 for _ in logits]
    return [e / s for e in exps]


def _argmax(xs: List[float]) -> int:
    best_i = 0
    best_v = xs[0] if xs else float("-inf")
    for i, v in enumerate(xs):
        if v > best_v:
            best_v = v
            best_i = i
    return best_i


def _mean_std(rows: List[List[float]]) -> Tuple[List[float], List[float]]:
    if not rows:
        return [], []
    n = len(rows)
    d = len(rows[0])

    mean = [0.0] * d
    for r in rows:
        for j, v in enumerate(r):
            mean[j] += v
    mean = [m / n for m in mean]

    var = [0.0] * d
    for r in rows:
        for j, v in enumerate(r):
            dv = v - mean[j]
            var[j] += dv * dv
    var = [v / max(1, n - 1) for v in var]

    std = [math.sqrt(v) for v in var]
    std = [s if s != 0.0 else 1.0 for s in std]
    return mean, std


def _standardize(rows: List[List[float]], mean: List[float], std: List[float]) -> List[List[float]]:
    out: List[List[float]] = []
    for r in rows:
        out.append([(r[j] - mean[j]) / std[j] for j in range(len(r))])
    return out


def _load_jsonl(path: str) -> List[Dict[str, Any]]:
    items: List[Dict[str, Any]] = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
                if isinstance(obj, dict):
                    items.append(obj)
            except Exception:
                continue
    return items


def _build_feature_index(examples: List[Dict[str, Any]]) -> Dict[str, int]:
    keys = set(DEFAULT_FEATURE_ORDER)
    for ex in examples:
        feats = ex.get("features")
        if isinstance(feats, dict):
            for k in feats.keys():
                if isinstance(k, str) and k:
                    keys.add(k)

    ordered: List[str] = []
    for k in DEFAULT_FEATURE_ORDER:
        if k in keys:
            ordered.append(k)
            keys.remove(k)
    ordered.extend(sorted(keys))

    return {k: i for i, k in enumerate(ordered)}


def _vectorize(ex: Dict[str, Any], feature_index: Dict[str, int]) -> List[float]:
    x = [0.0] * len(feature_index)
    feats = ex.get("features")
    if isinstance(feats, dict):
        for k, v in feats.items():
            if not isinstance(k, str):
                continue
            idx = feature_index.get(k)
            if idx is None:
                continue
            try:
                x[idx] = float(v)
            except Exception:
                x[idx] = 0.0
    return x


def _train_softmax_regression(
    X: List[List[float]],
    y: List[int],
    num_classes: int,
    epochs: int,
    lr: float,
    l2: float,
    seed: int,
    weights: List[float] | None = None,
) -> Tuple[List[List[float]], List[float]]:
    random.seed(seed)
    n = len(X)
    d = len(X[0]) if X else 0

    W = [[(random.random() - 0.5) * 0.01 for _ in range(d)] for _ in range(num_classes)]
    b = [0.0 for _ in range(num_classes)]

    if weights is None:
        weights = [1.0 for _ in range(n)]

    for epoch in range(1, epochs + 1):
        # batch gradients
        dW = [[0.0 for _ in range(d)] for _ in range(num_classes)]
        db = [0.0 for _ in range(num_classes)]

        correct = 0
        total_w = 0.0
        for i in range(n):
            xi = X[i]
            wi = float(weights[i]) if i < len(weights) else 1.0
            if wi <= 0.0:
                continue
            total_w += wi
            # logits
            logits = [b[c] for c in range(num_classes)]
            for c in range(num_classes):
                z = logits[c]
                wc = W[c]
                for j in range(d):
                    z += wc[j] * xi[j]
                logits[c] = z

            probs = _stable_softmax(logits)
            if _argmax(probs) == y[i]:
                correct += 1

            for c in range(num_classes):
                err = (probs[c] - (1.0 if y[i] == c else 0.0)) * wi
                db[c] += err
                wc_grad = dW[c]
                for j in range(d):
                    wc_grad[j] += err * xi[j]

        inv_n = 1.0 / max(1.0, total_w)
        for c in range(num_classes):
            db[c] *= inv_n
            for j in range(d):
                dW[c][j] = dW[c][j] * inv_n + l2 * W[c][j]

        # SGD update
        for c in range(num_classes):
            b[c] -= lr * db[c]
            for j in range(d):
                W[c][j] -= lr * dW[c][j]

        if epoch == 1 or epoch % 25 == 0 or epoch == epochs:
            acc = correct / max(1, n)
            print(f"epoch={epoch:4d}  train_acc={acc:.3f}")

    return W, b


def _accuracy(X: List[List[float]], y: List[int], W: List[List[float]], b: List[float]) -> float:
    if not X:
        return 0.0
    k = len(W)
    d = len(W[0]) if W else 0
    correct = 0
    for i, xi in enumerate(X):
        logits = [b[c] for c in range(k)]
        for c in range(k):
            z = logits[c]
            wc = W[c]
            for j in range(d):
                z += wc[j] * xi[j]
            logits[c] = z
        probs = _stable_softmax(logits)
        if _argmax(probs) == y[i]:
            correct += 1
    return correct / len(X)


def _group_accuracy(
    X: List[List[float]],
    y: List[int],
    group_keys: List[str],
    W: List[List[float]],
    b: List[float],
) -> float:
    # Each group counts once; correctness by majority label for that group.
    if not X:
        return 0.0
    groups: Dict[str, Dict[str, Any]] = {}
    for i, k in enumerate(group_keys):
        g = groups.setdefault(k, {"x": X[i], "ys": []})
        g["ys"].append(y[i])

    correct = 0
    total = 0
    for g in groups.values():
        xi = g["x"]
        ys: List[int] = g["ys"]
        # majority
        counts: Dict[int, int] = {}
        for yy in ys:
            counts[yy] = counts.get(yy, 0) + 1
        maj = max(counts.items(), key=lambda kv: kv[1])[0]

        logits = [b[c] for c in range(len(W))]
        for c in range(len(W)):
            z = logits[c]
            wc = W[c]
            for j in range(len(wc)):
                z += wc[j] * xi[j]
            logits[c] = z
        pred = _argmax(_stable_softmax(logits))
        if pred == maj:
            correct += 1
        total += 1
    return correct / max(1, total)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "--input",
        default=os.path.join(os.path.dirname(__file__), "..", "openai-proxy", "ml-logs", "training.jsonl"),
        help="Path to training.jsonl",
    )
    ap.add_argument(
        "--output",
        default=os.path.join(os.path.dirname(__file__), "..", "openai-proxy", "ml-model.json"),
        help="Output model JSON path",
    )
    ap.add_argument("--min-confidence", type=float, default=0.0)
    ap.add_argument("--epochs", type=int, default=200)
    ap.add_argument("--lr", type=float, default=0.15)
    ap.add_argument("--l2", type=float, default=1e-3)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--test-split", type=float, default=0.2)
    ap.add_argument(
        "--split-mode",
        choices=["random", "feature-hash", "exercise"],
        default="feature-hash",
        help="How to split train/test. 'feature-hash' avoids leakage from duplicate feature vectors.",
    )
    ap.add_argument(
        "--reweight-duplicates",
        action="store_true",
        help="Down-weight duplicate feature vectors during training (each unique feature vector has ~equal total weight).",
    )

    args = ap.parse_args()

    examples = _load_jsonl(args.input)
    if not examples:
        print(f"No examples found in: {args.input}")
        return 2

    # Filter by confidence if present
    filtered: List[Dict[str, Any]] = []
    for ex in examples:
        conf = ex.get("weakConfidence")
        try:
            conf_f = float(conf) if conf is not None else 1.0
        except Exception:
            conf_f = 1.0

        if conf_f >= args.min_confidence and isinstance(ex.get("weakLabel"), str):
            filtered.append(ex)

    if not filtered:
        print("All examples filtered out (confidence/label).")
        return 2

    feature_index = _build_feature_index(filtered)

    labels = sorted({str(ex["weakLabel"]) for ex in filtered if isinstance(ex.get("weakLabel"), str)})
    label_to_idx = {lab: i for i, lab in enumerate(labels)}

    X_all = [_vectorize(ex, feature_index) for ex in filtered]
    y_all = [label_to_idx[str(ex["weakLabel"]) ] for ex in filtered]

    # Train/test split
    if args.split_mode == "random":
        rnd = random.Random(args.seed)
        idxs = list(range(len(X_all)))
        rnd.shuffle(idxs)
        cut = int(round((1.0 - args.test_split) * len(idxs)))
        train_idx = idxs[:cut]
        test_idx = idxs[cut:]
    elif args.split_mode == "exercise":
        train_idx, test_idx = _split_grouped(filtered, _exercise_key, args.test_split, args.seed)
    else:
        train_idx, test_idx = _split_grouped(filtered, _feature_hash, args.test_split, args.seed)

    X_train = [X_all[i] for i in train_idx]
    y_train = [y_all[i] for i in train_idx]
    X_test = [X_all[i] for i in test_idx]
    y_test = [y_all[i] for i in test_idx]

    # Group keys (for leakage-free reporting)
    train_feat_hash = [_feature_hash(filtered[i]) for i in train_idx]
    test_feat_hash = [_feature_hash(filtered[i]) for i in test_idx]

    mean, std = _mean_std(X_train)
    X_train_s = _standardize(X_train, mean, std)
    X_test_s = _standardize(X_test, mean, std)

    weights: List[float] | None = None
    if args.reweight_duplicates:
        # Each unique feature vector gets ~equal total weight.
        counts: Dict[str, int] = {}
        for h in train_feat_hash:
            counts[h] = counts.get(h, 0) + 1
        weights = [1.0 / max(1, counts.get(h, 1)) for h in train_feat_hash]

    unique_train = len(set(train_feat_hash))
    unique_test = len(set(test_feat_hash))
    print(f"examples={len(filtered)}  train={len(X_train)}  test={len(X_test)}  split={args.split_mode}")
    print(f"unique_feature_vectors: train={unique_train} test={unique_test}")
    print(f"num_labels={len(labels)}  num_features={len(feature_index)}")

    W, b = _train_softmax_regression(
        X=X_train_s,
        y=y_train,
        num_classes=len(labels),
        epochs=args.epochs,
        lr=args.lr,
        l2=args.l2,
        seed=args.seed,
        weights=weights,
    )

    train_acc = _accuracy(X_train_s, y_train, W, b)
    test_acc = _accuracy(X_test_s, y_test, W, b) if X_test_s else 0.0
    train_acc_u = _group_accuracy(X_train_s, y_train, train_feat_hash, W, b)
    test_acc_u = _group_accuracy(X_test_s, y_test, test_feat_hash, W, b) if X_test_s else 0.0
    print(f"final_train_acc={train_acc:.3f}  final_test_acc={test_acc:.3f}")
    print(f"final_train_acc_unique={train_acc_u:.3f}  final_test_acc_unique={test_acc_u:.3f}")

    out_obj: Dict[str, Any] = {
        "labels": labels,
        "feature_index": feature_index,
        "weights": W,
        "bias": b,
        "standardize": {"mean": mean, "std": std},
    }

    out_path = os.path.abspath(args.output)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out_obj, f, indent=2)

    print(f"wrote: {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
