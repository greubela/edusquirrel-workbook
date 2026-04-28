#!/usr/bin/env bash
set -euo pipefail

# Build Scala.js fastOpt bundle via webpack (WSL)
# Usage:
#   bash tools/dev/build-fastopt.sh

# Resolve repo root reliably (works even if invoked from elsewhere).
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root=""
if command -v git >/dev/null 2>&1; then
  repo_root="$(git -C "$script_dir" rev-parse --show-toplevel 2>/dev/null || true)"
fi
if [[ -z "$repo_root" ]]; then
  repo_root="$(cd "$script_dir/../.." && pwd)"
fi

cd "$repo_root"

# SDKMAN (for sbt/java)
if [[ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  # shellcheck disable=SC1090
  set +u
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  set -u
fi

# Node 18 + webpack/OpenSSL workaround
export NODE_OPTIONS=--openssl-legacy-provider

sbt -batch -Dsbt.supershell=false "fastOptJS / webpack"

bundle="target/scala-3.3.3/scalajs-bundler/main/workbookapp-fastopt-bundle.js"
if [[ -f "$bundle" ]]; then
  if stat --version >/dev/null 2>&1; then
    # GNU stat (Linux / WSL)
    stat -c 'mtime=%y size=%s' "$bundle"
  else
    # BSD stat (macOS)
    stat -f 'mtime=%Sm size=%z' "$bundle"
  fi
else
  echo "Bundle not found: $bundle" >&2
  exit 1
fi
