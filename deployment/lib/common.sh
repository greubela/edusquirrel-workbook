# Shared helpers for the deployment scripts. Source this file; it is not executable on its own.
# shellcheck shell=bash

set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$DEPLOY_DIR/.." && pwd)"
SECRETS_DIR="${EDUSQUIRREL_SECRETS_DIR:-$DEPLOY_DIR/secrets}"

log() { printf '\033[1;34m[%s]\033[0m %s\n' "${LOG_PREFIX:-deploy}" "$*" >&2; }
warn() { printf '\033[1;33m[%s] warning:\033[0m %s\n' "${LOG_PREFIX:-deploy}" "$*" >&2; }
die() { printf '\033[1;31m[%s] error:\033[0m %s\n' "${LOG_PREFIX:-deploy}" "$*" >&2; exit 1; }

require_cmd() {
  local cmd
  for cmd in "$@"; do command -v "$cmd" >/dev/null 2>&1 || die "required command not found: $cmd"; done
}

require_vars() {
  local name
  for name in "$@"; do [[ -n "${!name:-}" ]] || die "$name is not set (see deployment/secrets/*.example)"; done
}

# Reads KEY=VALUE lines literally, as Docker Compose env files do for quoted values:
# no command substitution or interpolation; one pair of surrounding quotes is removed.
# Values containing '$' should be single-quoted so Compose does not interpolate them either.
env_file_entries() {
  local file=$1 line key value
  [[ -f "$file" ]] || die "missing $file (copy ${file}.example and fill it in)"
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ "$line" =~ ^[[:space:]]*(#|$) ]] && continue
    line="${line#export }"
    [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]] || die "$file: cannot parse line: $line"
    key="${BASH_REMATCH[1]}"
    value="${BASH_REMATCH[2]}"
    if [[ "$value" =~ ^\"(.*)\"$ || "$value" =~ ^\'(.*)\'$ ]]; then value="${BASH_REMATCH[1]}"; fi
    printf '%s\0%s\0' "$key" "$value"
  done < "$file"
}

# Exports every entry of a secrets file. The file is authoritative: variables exported in the
# calling shell (for example SQL_* from local development) never redirect a deployment.
load_env() {
  local key value
  env_file_entries "$SECRETS_DIR/$1" > "$DEPLOY_TMP/parsed-env"
  while IFS= read -r -d '' key && IFS= read -r -d '' value; do
    export "$key=$value"
  done < "$DEPLOY_TMP/parsed-env"
}

# Prints one entry of an env file without exporting it; fails when the key is absent.
env_file_value() {
  local file=$1 wanted=$2 key value
  env_file_entries "$file" > "$DEPLOY_TMP/parsed-value"
  while IFS= read -r -d '' key && IFS= read -r -d '' value; do
    [[ "$key" == "$wanted" ]] && { printf '%s' "$value"; return 0; }
  done < "$DEPLOY_TMP/parsed-value"
  return 1
}

# Quotes one argument for a POSIX remote shell.
shell_quote() { printf "'%s'" "${1//\'/\'\\\'\'}"; }

# Asks a [y/N] question and succeeds only for yes; --yes (DEPLOY_ASSUME_YES) answers it.
ask_yes_no() {
  local answer
  [[ "${DEPLOY_ASSUME_YES:-false}" == true ]] && return 0
  [[ -t 0 ]] || die "$1 Rerun interactively to answer, or pass --yes."
  read -r -p "$1 [y/N] " answer
  [[ "$answer" =~ ^[Yy]([Ee][Ss])?$ ]]
}

# Created in the main shell so pipelines and command substitutions share it.
DEPLOY_TMP="$(mktemp -d "${TMPDIR:-/tmp}/edusquirrel-deploy.XXXXXX")"
DEPLOY_SSH_AGENT_STARTED=false
DEPLOY_ON_EXIT=()
# Registers a command for cleanup; hooks run before the ssh-agent stops, so they may use SSH.
on_exit() { DEPLOY_ON_EXIT+=("$1"); }
deploy_cleanup() {
  local hook
  for hook in "${DEPLOY_ON_EXIT[@]+"${DEPLOY_ON_EXIT[@]}"}"; do eval "$hook" || true; done
  if [[ "$DEPLOY_SSH_AGENT_STARTED" == true ]]; then ssh-agent -k >/dev/null 2>&1 || true; fi
  rm -rf "$DEPLOY_TMP"
}
trap deploy_cleanup EXIT
# Interrupts exit through the EXIT trap, so temporary containers and the agent are cleaned up.
trap 'exit 130' INT TERM

git_version() { git -C "$REPO_ROOT" describe --always --dirty --tags 2>/dev/null || printf 'unknown'; }

# --- SSH to the backend server (settings from local.env) ---------------------------------

ssh_options() {
  local key="${SERVER_SSH_KEY/#\~/$HOME}"
  [[ -f "$key" ]] || die "SERVER_SSH_KEY does not point to a file: $SERVER_SSH_KEY"
  SSH_OPTS=(-i "$key" -o IdentitiesOnly=yes -o ServerAliveInterval=30
    -o "StrictHostKeyChecking=${SERVER_SSH_STRICT_HOST_KEY_CHECKING:-accept-new}")
  if [[ -n "${SERVER_SSH_KNOWN_HOSTS:-}" ]]; then
    SSH_OPTS+=(-o "UserKnownHostsFile=${SERVER_SSH_KNOWN_HOSTS/#\~/$HOME}")
  fi
}

# Unlocks the key once through a private ssh-agent so every later ssh call is non-interactive.
# Call it in the main shell before SSH is used inside pipelines or command substitutions.
ssh_init() {
  [[ -n "${SSH_OPTS+set}" ]] && return 0
  require_cmd ssh
  require_vars SERVER_SSH_HOST SERVER_SSH_USER SERVER_SSH_KEY
  ssh_options
  if [[ -n "${SERVER_SSH_KEY_PASSPHRASE:-}" ]]; then
    require_cmd ssh-agent ssh-add
    eval "$(ssh-agent -s)" >/dev/null
    DEPLOY_SSH_AGENT_STARTED=true
    local askpass="$DEPLOY_TMP/askpass.sh"
    # shellcheck disable=SC2016  # The askpass script expands the variable itself.
    printf '#!/bin/sh\nprintf "%%s\\n" "$SERVER_SSH_KEY_PASSPHRASE"\n' > "$askpass"
    chmod 700 "$askpass"
    SSH_ASKPASS="$askpass" SSH_ASKPASS_REQUIRE=force DISPLAY="${DISPLAY:-none}" \
      ssh-add "${SERVER_SSH_KEY/#\~/$HOME}" < /dev/null >/dev/null 2>&1 \
      || die "could not unlock SERVER_SSH_KEY with SERVER_SSH_KEY_PASSPHRASE (OpenSSH 8.4+ needed)"
    SSH_OPTS+=(-o BatchMode=yes)
  fi
}

# Runs a POSIX shell command on the server; stdin is forwarded.
ssh_run() {
  ssh_init
  ssh "${SSH_OPTS[@]}" -p "${SERVER_SSH_PORT:-22}" "$SERVER_SSH_USER@$SERVER_SSH_HOST" "$1"
}

# Uploads a local file through the SSH channel itself (no SFTP subsystem needed).
ssh_put() {
  ssh_run "umask 077 && cat > $(shell_quote "$2")" < "$1"
}
