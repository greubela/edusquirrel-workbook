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
#   ./deploy.sh -- --dry-run         Pass the remaining options to deploy-client.sh
#
# Every run is logged to tmp/work/deployment/logs/. On Windows, run it in Git Bash, or from
# PowerShell with: & "$env:ProgramFiles\Git\bin\bash.exe" ./deploy.sh
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
    --) shift; client_args=("$@"); break ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
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
  if [[ "${SHLVL:-1}" -le 1 && -t 0 ]]; then read -r -p "Press Enter to close this window. " _ || true; fi
  exit "$status"
fi
log "log file: ${DEPLOY_LOG_FILE#"$REPO_ROOT"/}"

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
