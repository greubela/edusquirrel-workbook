#!/usr/bin/env bash
# Uploads server.jar, docker-compose.yml, update-docker.sh and the .env built from
# secrets/server.env, then recreates the Compose services. SERVER_REMOTE_DIR mirrors the
# repository root: the jar goes to artifacts/newest/, everything else to deployment/server/.
# Options: --skip-health-check (do not query the public /health endpoint afterwards).
# shellcheck source=SCRIPTDIR/../lib/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"
LOG_PREFIX=server

public_check=true
for arg in "$@"; do
  case "$arg" in
    --skip-health-check) public_check=false ;;
    *) die "unknown argument: $arg" ;;
  esac
done

load_env local.env
require_vars SERVER_SSH_HOST SERVER_SSH_USER SERVER_SSH_KEY SERVER_REMOTE_DIR
SERVER_ENV="$SECRETS_DIR/server.env"
JAR="$REPO_ROOT/artifacts/newest/server.jar"
[[ -f "$JAR" ]] || die "missing artifacts/newest/server.jar; run 'sbt buildServerDeploy' first"

# Validate the file in this shell; the lookups below run in command substitutions.
env_file_entries "$SERVER_ENV" > /dev/null
for key in SQL_HOST SQL_DATABASE SQL_USER SQL_PW; do
  [[ -n "$(env_file_value "$SERVER_ENV" "$key" || true)" ]] || die "server.env must set $key"
done
[[ -n "$(env_file_value "$SERVER_ENV" SERVER_SECRET || true)" ]] || die "server.env must set SERVER_SECRET"
# Compose expands '$' outside single quotes, so the backend would see a different value.
while IFS= read -r line || [[ -n "$line" ]]; do
  if [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=([^\'].*)?\$ ]]; then
    die "server.env: single-quote the value of ${BASH_REMATCH[1]}; Docker Compose would expand its '\$'"
  fi
done < "$SERVER_ENV"

version="$(git_version)"
grep -v '^[[:space:]]*SERVER_VERSION=' "$SERVER_ENV" > "$DEPLOY_TMP/server.env" || true
printf '\nSERVER_VERSION=%s\n' "$version" >> "$DEPLOY_TMP/server.env"
chmod 600 "$DEPLOY_TMP/server.env"

ssh_init
remote_root="${SERVER_REMOTE_DIR%/}"
compose_dir="$remote_root/deployment/server"
log "uploading server $version to $SERVER_SSH_USER@$SERVER_SSH_HOST:$remote_root"
ssh_run "mkdir -p $(shell_quote "$remote_root/artifacts/newest") $(shell_quote "$compose_dir")"
ssh_put "$JAR" "$remote_root/artifacts/newest/server.jar.upload"
ssh_put_text "$DEPLOY_DIR/server/docker-compose.yml" "$compose_dir/docker-compose.yml.upload"
ssh_put_text "$DEPLOY_TMP/server.env" "$compose_dir/.env.upload"
ssh_put_text "$DEPLOY_DIR/server/update-docker.sh" "$compose_dir/update-docker.sh"
ssh_run "sh $(shell_quote "$compose_dir/update-docker.sh")" || die "server update failed (see the output above)"

if [[ "$public_check" == true && -n "${SERVER_HEALTH_URL:-}" ]]; then
  require_cmd curl
  # The proxy restarts with the backend; give it a moment to answer.
  health="$(curl --silent --show-error --fail --max-time 20 --retry 6 --retry-delay 5 --retry-all-errors \
    "$SERVER_HEALTH_URL")" \
    || die "public health check failed: $SERVER_HEALTH_URL"
  [[ "$health" == *"\"version\":\"$version\""* ]] \
    || die "public health endpoint does not report version $version: $health"
  log "public health check passed: $SERVER_HEALTH_URL"
fi
log "server $version deployed"
