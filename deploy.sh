#!/usr/bin/env bash
# One-click deployment: build, check the database, update the backend, upload the website.
# See docs/deployment.md. Settings and credentials come from deployment/secrets/*.env.
#
#   ./deploy.sh                      Build everything and deploy all parts
#   ./deploy.sh --skip-build         Deploy the artifacts that are already built
#   ./deploy.sh --only=server,client Run only the named steps (build, database, server, client)
#   ./deploy.sh --database=setup     Create the schema; asks before re-creating existing tables
#                                    (default: check only)
#   ./deploy.sh --yes                Answer yes to all questions
#   ./deploy.sh --no-pull            Deploy the checkout as it is, without git fetch/pull first
#   ./deploy.sh -- --dry-run         Pass the remaining options to deploy-client.sh
#
# Every run first fetches and fast-forwards the current branch, and is logged to
# tmp/work/deployment/logs/. On Windows, run it in Git Bash, or from PowerShell with:
#   & "$env:ProgramFiles\Git\bin\bash.exe" ./deploy.sh

# Whether this run has a window of its own (double-click, PowerShell), decided once before re-starts.
if [[ -z "${DEPLOY_OWN_WINDOW:-}" ]]; then
  if [[ "${SHLVL:-1}" -le 1 && -t 0 ]]; then DEPLOY_OWN_WINDOW=true; else DEPLOY_OWN_WINDOW=false; fi
  export DEPLOY_OWN_WINDOW
fi

# Fetch and pull first, from a temporary copy of this script: Git for Windows cannot replace a script
# that a running shell holds open. Afterwards the freshly pulled deploy.sh runs the deployment.
if [[ -z "${DEPLOY_PULLED:-}" && " $* " != *" --no-pull "* && " $* " != *" -h "* && " $* " != *" --help "* ]]; then
  if [[ -z "${DEPLOY_PULL_REPO:-}" ]]; then
    DEPLOY_PULL_REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    copy="$(mktemp "${TMPDIR:-/tmp}/edusquirrel-deploy-pull.XXXXXX")"
    cp "$DEPLOY_PULL_REPO/deploy.sh" "$copy"
    export DEPLOY_PULL_REPO
    exec bash "$copy" "$@"
  fi
  rm -f "${BASH_SOURCE[0]}"
  pull_failed() {
    printf '[deploy] error: %s; resolve it, or run with --no-pull to deploy the checkout as it is\n' "$1" >&2
    if [[ "$DEPLOY_OWN_WINDOW" == true ]]; then read -r -p "Press Enter to close this window. " _ || true; fi
    exit 1
  }
  cd "$DEPLOY_PULL_REPO" || pull_failed "cannot enter $DEPLOY_PULL_REPO"
  branch="$(git symbolic-ref --quiet --short HEAD)" || pull_failed "the checkout is not on a branch"
  printf '[deploy] fetching and pulling %s\n' "$branch" >&2
  git fetch --prune --quiet || pull_failed "git fetch failed"
  git pull --ff-only || pull_failed "git pull failed (local commits or conflicting local changes?)"
  export DEPLOY_PULLED=1
  exec bash "$DEPLOY_PULL_REPO/deploy.sh" "$@"
fi

# shellcheck source=SCRIPTDIR/deployment/lib/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/deployment/lib/common.sh"
LOG_PREFIX=deploy

original_args=("$@")
steps=",build,database,server,client," database_mode=check yes=() client_args=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build) steps="${steps/,build,/,}" ;;
    --only=*) steps=",${1#--only=},"
      for step in ${steps//,/ }; do [[ ",build,database,server,client," == *",$step,"* ]] || die "unknown step: $step"; done ;;
    --database=*) database_mode="${1#--database=}"
      [[ "$database_mode" =~ ^(check|setup)$ ]] || die "--database must be check or setup" ;;
    --yes) yes=(--yes) ;;
    --no-pull) ;;  # Handled before the pull above.
    --) shift; client_args=("$@"); break ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
  shift
done
has_step() { [[ "$steps" == *",$1,"* ]]; }

# Log everything to a file as well as the console: the deployment runs as a child whose output is
# piped through tee. (Redirecting this shell with exec > >(tee ...) loses output in Git Bash on Windows.)
if [[ -z "${DEPLOY_LOG_FILE:-}" ]]; then
  DEPLOY_LOG_FILE="$REPO_ROOT/tmp/work/deployment/logs/deploy-$(date +%Y%m%d-%H%M%S)-$$.log"
  mkdir -p "${DEPLOY_LOG_FILE%/*}"
  export DEPLOY_LOG_FILE NO_COLOR=1
  set +e
  bash "$REPO_ROOT/deploy.sh" ${original_args[@]+"${original_args[@]}"} 2>&1 | tee -a "$DEPLOY_LOG_FILE"
  status=${PIPESTATUS[0]}
  set -e
  relative_log="${DEPLOY_LOG_FILE#"$REPO_ROOT"/}"
  if [[ ! -s "$DEPLOY_LOG_FILE" ]]; then warn "the log file $relative_log could not be written"; fi
  if [[ "$status" -ne 0 ]]; then
    printf '[deploy] FAILED (exit %s); see the messages above and %s\n' "$status" "$relative_log" | tee -a "$DEPLOY_LOG_FILE" >&2
  fi
  # Started by a double-click or from PowerShell, the window would close before the output can be read.
  if [[ "$DEPLOY_OWN_WINDOW" == true ]]; then read -r -p "Press Enter to close this window. " _ || true; fi
  exit "$status"
fi
log "log file: ${DEPLOY_LOG_FILE#"$REPO_ROOT"/}"
log "deploying $(git -C "$REPO_ROOT" log -1 --format='%h %s' 2>/dev/null || echo 'an unknown revision')"

# Fail before the long build when a step's settings are missing.
needed="$(has_step database && echo "local.env server.env"; has_step server && echo "local.env server.env"
  has_step client && echo "local.env client.env"; true)"
for file in $needed; do
  [[ -f "$SECRETS_DIR/$file" ]] || die "missing $SECRETS_DIR/$file (copy $file.example and fill it in)"
done
if [[ -n "$(git -C "$REPO_ROOT" status --porcelain --untracked-files=no 2>/dev/null)" ]]; then
  warn "the working tree has uncommitted changes; the server reports version $(git_version)"
fi

# Build only what the selected steps deploy (everything when only "build" is selected).
if has_step build; then
  tasks=()
  if has_step server; then tasks+=(buildServerDeploy); fi
  if has_step client; then tasks+=(buildClientDeploy buildWorkerDeploy); fi
  if [[ ${#tasks[@]} -eq 0 ]]; then tasks=(buildAllDeploy); fi
  require_cmd sbt
  log "building: sbt ${tasks[*]}"
  (cd "$REPO_ROOT" && sbt -batch "${tasks[@]}") || die "sbt build failed"
fi
# "bash" runs the steps even where the execute bit or the #! line is not honored (Windows).
if has_step database; then
  bash "$DEPLOY_DIR/database/deploy-database.sh" "$database_mode" "${yes[@]+"${yes[@]}"}" || die "database step failed"
fi
if has_step server; then
  bash "$DEPLOY_DIR/server/deploy-server.sh" || die "server step failed"
fi
if has_step client; then
  bash "$DEPLOY_DIR/client/deploy-client.sh" "${client_args[@]+"${client_args[@]}"}" || die "website step failed"
fi
log "finished: ${steps//,/ }"
