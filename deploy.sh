#!/usr/bin/env bash
# One-click deployment: build, check the database, update the backend, upload the website.
# See docs/deployment.md. Settings and credentials come from deployment/secrets/*.env.
#
#   ./deploy.sh                      Build everything and deploy all parts
#   ./deploy.sh --skip-build         Deploy the artifacts that are already built
#   ./deploy.sh --only=server,client Run only the named steps (build, database, server, client)
#   ./deploy.sh --database=reset     Recreate the schema (setup | reset; default: check only)
#   ./deploy.sh --yes                Do not ask for confirmations
#   ./deploy.sh -- --dry-run         Pass the remaining options to deploy-client.sh
# shellcheck source=SCRIPTDIR/deployment/lib/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/deployment/lib/common.sh"
LOG_PREFIX=deploy

steps=",build,database,server,client," database_mode=check yes=() client_args=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build) steps="${steps/,build,/,}" ;;
    --only=*) steps=",${1#--only=},"
      for step in ${steps//,/ }; do [[ ",build,database,server,client," == *",$step,"* ]] || die "unknown step: $step"; done ;;
    --database=*) database_mode="${1#--database=}"
      [[ "$database_mode" =~ ^(check|setup|reset)$ ]] || die "--database must be check, setup or reset" ;;
    --yes) yes=(--yes) ;;
    --) shift; client_args=("$@"); break ;;
    -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
  shift
done
has_step() { [[ "$steps" == *",$1,"* ]]; }

# Fail before the long build when a step's settings are missing.
needed="$(has_step database && echo "local.env database.env server.env"; has_step server && echo "local.env server.env"
  has_step client && echo "local.env client.env"; true)"
for file in $needed; do
  [[ -f "$SECRETS_DIR/$file" ]] || die "missing $SECRETS_DIR/$file (copy $file.example and fill it in)"
done
if [[ -n "$(git -C "$REPO_ROOT" status --porcelain --untracked-files=no 2>/dev/null)" ]]; then
  warn "the working tree has uncommitted changes; the server reports version $(git_version)"
fi

if has_step build; then
  require_cmd sbt npm
  log "building client, worker and server (sbt buildAllDeploy)"
  (cd "$REPO_ROOT" && sbt -batch buildAllDeploy)
  log "building MathWorld"
  (cd "$REPO_ROOT/resources/programs/20260907MathworldMain" && npm ci && npm run build -- --base=./)
fi
if has_step database; then
  "$DEPLOY_DIR/database/deploy-database.sh" "$database_mode" "${yes[@]+"${yes[@]}"}"
fi
if has_step server; then
  "$DEPLOY_DIR/server/deploy-server.sh"
fi
if has_step client; then
  "$DEPLOY_DIR/client/deploy-client.sh" "${client_args[@]+"${client_args[@]}"}"
fi
log "finished: ${steps//,/ }"
