#!/usr/bin/env bash
set -euo pipefail

# Repeatable dataset generation from hand-written/mutated submission variants.
# Requires:
# - Proxy running (your python app.py) for /api/ml/log-example
# - Node 18+ for Scala.js test runner
# - python3 for executing unit tests

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

# portable_stat <file> – prints mtime + size on both GNU (Linux/WSL) and BSD (macOS) stat
portable_stat() {
  if stat --version >/dev/null 2>&1; then
    stat -c 'mtime=%y size=%s' "$1"
  else
    stat -f 'mtime=%Sm size=%z' "$1"
  fi
}

# sdkman is used for sbt/java in this repo.
if [[ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  set +u
  # shellcheck disable=SC1090
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  set -u
fi

PROXY_BASE="${ML_PROXY_BASE:-http://127.0.0.1:8000}"
export ML_PROXY_BASE="$PROXY_BASE"
export ML_LOG_URL="${ML_LOG_URL:-$PROXY_BASE/api/ml/log-example}"
export PYTHON_BIN="${PYTHON_BIN:-python3}"
export AUTO_SUBMISSIONS=1
export AUTO_SAVE_CODE="${AUTO_SAVE_CODE:-1}"

IN_PATH="tools/openai-proxy/ml-logs/training.jsonl"
OUT_PATH="tools/openai-proxy/ml-model.json"
SUBMISSIONS_DIR="tools/openai-proxy/ml-logs/submissions"

if [[ "${AUTO_RESET:-0}" == "1" ]]; then
  echo "--- RESET enabled: deleting old training artifacts"
  rm -f "$IN_PATH" || true
  rm -f "$OUT_PATH" || true
  rm -rf "$SUBMISSIONS_DIR" || true
fi

# Adaptive mutation-search targets (per exercise): prefer 30-50 unique, label-balanced examples.
export AUTO_TARGET_MIN="${AUTO_TARGET_MIN:-30}"
export AUTO_TARGET_MAX="${AUTO_TARGET_MAX:-50}"
export AUTO_MUTATION_TRIES="${AUTO_MUTATION_TRIES:-800}"

# Optional: comma-separated exercise ids. If empty, uses all ids known in the generator.
export AUTO_EXERCISES="${AUTO_EXERCISES:-}"

echo "Proxy base:      $ML_PROXY_BASE"
echo "ML log url:      $ML_LOG_URL"
echo "Python runner:   $PYTHON_BIN"
echo "Exercises:       ${AUTO_EXERCISES:-<all> }"
echo "Target/exercise: ${AUTO_TARGET_MIN}-${AUTO_TARGET_MAX}"
echo "Max tries/ex:    ${AUTO_MUTATION_TRIES}"
echo "Save code:       ${AUTO_SAVE_CODE} (to $SUBMISSIONS_DIR/<exerciseId>/...)"

echo "--- Running Scala.js generator test"
sbt -batch -Dsbt.supershell=false -no-colors "testOnly interactionPlugins.blockEnvironment.feedback.ml.AutoSubmissionsMlDatasetSpec"

echo "--- Training offline model from JSONL"
if [[ ! -f "$IN_PATH" ]]; then
  echo "No training data found at $IN_PATH" >&2
  exit 2
fi

before="$(portable_stat "$OUT_PATH" 2>/dev/null || echo 'missing')"
lines="$(wc -l < "$IN_PATH" | tr -d ' ')"

echo "training.jsonl lines=$lines"
echo "ml-model.json before: $before"

TRAIN_PY="$ROOT_DIR/tools/openai-proxy/.venv/bin/python"
if [[ ! -x "$TRAIN_PY" ]]; then
  TRAIN_PY="${PYTHON_BIN:-python3}"
fi

"$TRAIN_PY" "$ROOT_DIR/tools/dev/train_mini_ml.py" \
  --input "$IN_PATH" \
  --output "$OUT_PATH" \
  --epochs "${AUTO_TRAIN_EPOCHS:-200}" \
  --lr "${AUTO_TRAIN_LR:-0.15}" \
  --l2 "${AUTO_TRAIN_L2:-1e-3}" \
  --min-confidence "${AUTO_TRAIN_MIN_CONFIDENCE:-0.0}" \
  --split-mode "${AUTO_TRAIN_SPLIT_MODE:-feature-hash}" \
  --reweight-duplicates \
  | head -n 80

after="$(portable_stat "$OUT_PATH")"
echo "ml-model.json after:  $after"
