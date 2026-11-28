#!/usr/bin/env bash
# Mirrors the published part of the repository to CLIENT_FTP_ROOT over FTPS, in the repository's
# layout: homepage/, resources/, favicon.ico, the client and worker of artifacts/newest and
# artifacts/stable (never artifacts/history) and secrets/client.env as deployment/client/.env.
# Only files that changed since the last deployment are uploaded; every upload is checked by size.
# The transfer runs in Node (webspace-sync.mjs, basic-ftp), independent of the local curl.
#
# Options:
#   --dry-run          Show what would be uploaded or deleted without changing the webspace
#   --trust PREFIX     On a first deployment, treat files under PREFIX (e.g. resources/workbookpdfs)
#                      as already present instead of uploading them; repeatable
# shellcheck source=SCRIPTDIR/../lib/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"
LOG_PREFIX=client

sync_args=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) sync_args+=(--dry-run) ;;
    --trust) [[ $# -ge 2 ]] || die "--trust needs a path prefix"; sync_args+=(--trust "$2"); shift ;;
    *) die "unknown argument: $1" ;;
  esac
  shift
done

# local.env exports CLIENT_FTP_*; webspace-sync.mjs reads the connection from the environment.
load_env local.env
require_vars CLIENT_FTP_HOST CLIENT_FTP_USER CLIENT_FTP_PASSWORD
require_cmd node npm
CLIENT_ENV="$SECRETS_DIR/client.env"
SYNC="$DEPLOY_DIR/client/webspace-sync.mjs"

# deployment/client/.env is publicly downloadable from the website.
env_file_entries "$CLIENT_ENV" > "$DEPLOY_TMP/client-env"
while IFS= read -r -d '' key && IFS= read -r -d '' _; do
  if [[ "$(printf '%s' "$key" | tr '[:lower:]' '[:upper:]')" =~ (PASS|PW|SECRET|TOKEN|KEY|CREDENTIAL) ]]; then
    die "client.env is public on the website and must not contain $key"
  fi
done < "$DEPLOY_TMP/client-env"

# The FTP client is a locked dev dependency of the repository.
if ! (cd "$REPO_ROOT" && node -e 'import("basic-ftp")' >/dev/null 2>&1); then
  log "installing the repository's Node dependencies (npm ci)"
  (cd "$REPO_ROOT" && npm ci --no-audit --no-fund) || die "npm ci failed"
fi

# The webspace mirrors the repository layout; stage its published part.
SITE="$REPO_ROOT/tmp/work/deployment/webspace"
log "staging homepage, resources, artifacts (newest, stable) and deployment/client/.env"
node "$SYNC" stage "$REPO_ROOT" "$SITE" "$CLIENT_ENV" || die "staging the webspace failed (see above)"

node "$SYNC" sync "$SITE" ${sync_args[@]+"${sync_args[@]}"} \
  || die "webspace upload failed (see above); rerun to continue, finished batches are recorded"
