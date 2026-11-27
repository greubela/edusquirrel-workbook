#!/usr/bin/env bash
# Uploads server.jar, docker-compose.yml, update-docker.sh and the .env built from
# secrets/server.env to SERVER_REMOTE_DIR, then recreates the Compose services.
# Options: --skip-health-check (do not query the public /health endpoint afterwards).
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

# The backend must use the database that deploy-database.sh checks.
for key in SQL_HOST SQL_PORT SQL_DATABASE SQL_USER SQL_PW; do
  server_value="$(env_file_value "$SERVER_ENV" "$key")" || die "server.env does not set $key"
  database_value="$(env_file_value "$SECRETS_DIR/database.env" "$key")" || die "database.env does not set $key"
  [[ "$server_value" == "$database_value" ]] || die "$key differs between server.env and database.env"
done
[[ -n "$(env_file_value "$SERVER_ENV" SERVER_SECRET || true)" ]] || die "server.env must set SERVER_SECRET"

version="$(git_version)"
grep -v '^[[:space:]]*SERVER_VERSION=' "$SERVER_ENV" > "$DEPLOY_TMP/server.env" || true
printf '\nSERVER_VERSION=%s\n' "$version" >> "$DEPLOY_TMP/server.env"
chmod 600 "$DEPLOY_TMP/server.env"

ssh_init
remote_dir="$(shell_quote "$SERVER_REMOTE_DIR")"
log "uploading server $version to $SERVER_SSH_USER@$SERVER_SSH_HOST:$SERVER_REMOTE_DIR"
ssh_run "mkdir -p $remote_dir/artifacts/newest"
ssh_put "$JAR" "$SERVER_REMOTE_DIR/artifacts/newest/server.jar.upload"
ssh_put "$DEPLOY_DIR/server/docker-compose.yml" "$SERVER_REMOTE_DIR/docker-compose.yml.upload"
ssh_put "$DEPLOY_TMP/server.env" "$SERVER_REMOTE_DIR/.env.upload"
ssh_put "$DEPLOY_DIR/server/update-docker.sh" "$SERVER_REMOTE_DIR/update-docker.sh"
ssh_run "sh $remote_dir/update-docker.sh"

if [[ "$public_check" == true && -n "${SERVER_HEALTH_URL:-}" ]]; then
  require_cmd curl
  health="$(curl --silent --show-error --fail --max-time 20 "$SERVER_HEALTH_URL")" \
    || die "public health check failed: $SERVER_HEALTH_URL"
  [[ "$health" == *"\"version\":\"$version\""* ]] \
    || die "public health endpoint does not report version $version: $health"
  log "public health check passed: $SERVER_HEALTH_URL"
fi
log "server $version deployed"
