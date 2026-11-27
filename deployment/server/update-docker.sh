#!/bin/sh
# Runs on the backend server inside SERVER_REMOTE_DIR (uploaded by deploy-server.sh).
#   sh update-docker.sh             Activate the *.upload files, recreate the services, wait for health;
#                                   if the backend stays unhealthy, keep the new files as *.failed and
#                                   roll back to the previous version automatically
#   sh update-docker.sh --rollback  Restore the *.previous files and recreate the services
# Files: docker-compose.yml, .env, artifacts/newest/server.jar
set -eu
cd "$(dirname "$0")"
FILES="docker-compose.yml .env artifacts/newest/server.jar"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"

log() { printf '[server] %s\n' "$*" >&2; }

restart_services() {
  docker compose config --quiet
  log "docker compose down"
  docker compose down --remove-orphans
  log "docker compose up -d"
  docker compose up -d
}

wait_for_health() {
  container="$(docker compose ps -q edusquirrel)"
  [ -n "$container" ] || { log "edusquirrel container is not running"; return 1; }
  waited=0
  while [ "$waited" -lt "$HEALTH_TIMEOUT" ]; do
    status="$(docker inspect --format '{{.State.Health.Status}}' "$container" 2>/dev/null || echo missing)"
    case "$status" in
      healthy) log "edusquirrel is healthy after ${waited}s"; return 0 ;;
      unhealthy|missing) break ;;
    esac
    sleep 5; waited=$((waited + 5))
  done
  log "edusquirrel did not become healthy (status: ${status:-unknown}); recent logs:"
  docker compose logs --tail 60 edusquirrel >&2 || true
  return 1
}

rollback() {
  for file in $FILES; do
    if [ -f "$file.previous" ]; then cp -p "$file.previous" "$file" && log "restored $file"; fi
  done
  restart_services
  wait_for_health
}

if [ "${1:-}" = "--rollback" ]; then
  rollback
  exit
fi

for file in $FILES; do
  [ -f "$file.upload" ] || { log "missing $file.upload"; exit 1; }
done
for file in $FILES; do
  if [ -f "$file" ]; then cp -p "$file" "$file.previous"; fi
  mv "$file.upload" "$file"
done
chmod 600 .env
if restart_services && wait_for_health; then exit 0; fi

for file in $FILES; do cp -p "$file" "$file.failed"; done
if [ -f artifacts/newest/server.jar.previous ]; then
  log "deployment failed; rolling back (the new files are kept as *.failed)"
  rollback && log "previous version restored"
else
  log "deployment failed and no previous version exists"
fi
exit 1
