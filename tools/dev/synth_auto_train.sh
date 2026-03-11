#!/usr/bin/env bash
set -euo pipefail

# Dev-only end-to-end loop:
# 1) Start tools/openai-proxy/app.py (with ENABLE_SYNTH_ENDPOINT=1, ENABLE_ML_TRAIN_ENDPOINT=1)
# 2) Generate synthetic solutions via LLM
# 3) Execute tests locally via python
# 4) Log features to /api/ml/log-example
# 5) Trigger /api/ml/train to export tools/openai-proxy/ml-model.json

cd "$(dirname "$0")/../.."

: "${SYNTH_PROXY_BASE:=http://127.0.0.1:8000}"
: "${ML_LOG_URL:=$SYNTH_PROXY_BASE/api/ml/log-example}"

export SYNTH_GENERATE=1
export SYNTH_TRAIN=1
export SYNTH_PROXY_BASE
export ML_LOG_URL

# Optional knobs:
# export SYNTH_EXERCISES="block:gcd,block:is-palindrome"
# export SYNTH_PER_EXERCISE=8
# export PYTHON_BIN=python3
# export SYNTH_TRAIN_EPOCHS=250

# sdkman-init.sh is not always nounset-safe.
if [[ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
	set +u
	# shellcheck disable=SC1090
	source "$HOME/.sdkman/bin/sdkman-init.sh"
	set -u
fi

sbt -batch -Dsbt.supershell=false -no-colors "testOnly interactionPlugins.blockEnvironment.feedback.ml.SyntheticMlDatasetGeneratorSpec"

echo "OK: generated logs and trained model (if endpoints enabled)"
