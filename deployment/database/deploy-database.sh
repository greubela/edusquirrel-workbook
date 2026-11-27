#!/usr/bin/env bash
# Checks or (re)creates the EduSquirrel schema in the dedicated MySQL database.
#
#   deploy-database.sh check   Compare the live schema with setup.sql (default; changes nothing)
#   deploy-database.sh setup   Run setup.sql on an empty database, then check
#   deploy-database.sh reset   Back up, drop every table/view/routine, run setup.sql, then check
#
# Options: --yes (skip the confirmation prompt), --no-backup (reset without a dump).
# MySQL clients run in Docker on DATABASE_TOOLS_HOST: "server" (the backend host via SSH,
# default) or "local". The reference schema for "check" is built from setup.sql in a
# throwaway MySQL container on the same host, so no database other than SQL_DATABASE is touched.
source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"
LOG_PREFIX=database

mode=check backup=true
for arg in "$@"; do
  case "$arg" in
    check|setup|reset) mode=$arg ;;
    --yes) DEPLOY_ASSUME_YES=true ;;
    --no-backup) backup=false ;;
    *) die "unknown argument: $arg" ;;
  esac
done

load_env local.env
load_env database.env
require_vars SQL_HOST SQL_DATABASE SQL_USER SQL_PW
SQL_PORT="${SQL_PORT:-3306}"
MYSQL_IMAGE="${DATABASE_MYSQL_IMAGE:-mysql:8.4}"
SETUP_SQL="$DEPLOY_DIR/database/setup.sql"
if [[ "${DATABASE_TOOLS_HOST:-server}" == server ]]; then ssh_init; fi

# Runs a shell command on the host that owns Docker for the MySQL clients.
on_tools_host() {
  case "${DATABASE_TOOLS_HOST:-server}" in
    server) ssh_run "$1" ;;
    local) require_cmd docker; sh -c "$1" ;;
    *) die "DATABASE_TOOLS_HOST must be 'server' or 'local'" ;;
  esac
}

# Runs a MySQL client program against SQL_DATABASE; SQL arrives on stdin.
# The password is sent as the first stdin line so it never appears in a command line.
remote_client() {
  local program=$1; shift
  local args=(--host="$SQL_HOST" --port="$SQL_PORT" --user="$SQL_USER" "$@") quoted="" arg
  for arg in "${args[@]}"; do quoted+=" $(shell_quote "$arg")"; done
  { printf '%s\n' "$SQL_PW"; cat; } | on_tools_host \
    "IFS= read -r MYSQL_PWD && export MYSQL_PWD && exec docker run --rm -i -e MYSQL_PWD $(shell_quote "$MYSQL_IMAGE") $program$quoted"
}
remote_sql() {
  remote_client mysql --batch --skip-column-names --default-character-set=utf8mb4 "$@" "$SQL_DATABASE"
}

# One sorted line per table, column, index, foreign key and seed row. Values compare as text.
fingerprint_sql() {
  cat <<'SQL'
SELECT 'table', TABLE_NAME, TABLE_TYPE, IFNULL(ENGINE, ''), IFNULL(TABLE_COLLATION, '')
  FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE();
SELECT 'column', TABLE_NAME, LPAD(ORDINAL_POSITION, 3, '0'), COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE,
       IFNULL(COLUMN_DEFAULT, 'NULL'), EXTRA, IFNULL(COLLATION_NAME, '')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE();
SELECT 'index', TABLE_NAME, INDEX_NAME, LPAD(SEQ_IN_INDEX, 3, '0'), COLUMN_NAME, NON_UNIQUE
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE();
SELECT 'foreign-key', k.TABLE_NAME, k.CONSTRAINT_NAME, k.COLUMN_NAME, k.REFERENCED_TABLE_NAME,
       k.REFERENCED_COLUMN_NAME, r.UPDATE_RULE, r.DELETE_RULE
  FROM information_schema.KEY_COLUMN_USAGE k
  JOIN information_schema.REFERENTIAL_CONSTRAINTS r
    ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
 WHERE k.TABLE_SCHEMA = DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL;
SELECT 'routine', ROUTINE_TYPE, ROUTINE_NAME FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = DATABASE();
SQL
  # The seed row is part of setup.sql; query it only when its table exists.
  printf '%s\n' "SET @seed = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user') = 1, 'SELECT ''seed-row'', id FROM \`user\` WHERE id = ''user-unknown''', 'DO 0');" \
    "PREPARE seed FROM @seed; EXECUTE seed; DEALLOCATE PREPARE seed;"
}

reference_fingerprint() {
  local name="edusquirrel-schema-reference-$$-$RANDOM"
  log "building the reference schema from setup.sql in a temporary $MYSQL_IMAGE container"
  on_exit "on_tools_host 'docker rm -f $name >/dev/null 2>&1'"
  on_tools_host "docker run -d --rm --name $name --tmpfs /var/lib/mysql -e MYSQL_ALLOW_EMPTY_PASSWORD=yes \
    -e MYSQL_DATABASE=reference $(shell_quote "$MYSQL_IMAGE") >/dev/null" >&2
  # The image's init server listens only on the socket; TCP answers once the final server runs.
  on_tools_host "for i in \$(seq 1 90); do docker exec $name mysql -h127.0.0.1 -uroot -e 'SELECT 1' reference \
    >/dev/null 2>&1 && exit 0; sleep 2; done; docker logs --tail 30 $name >&2; exit 1" \
    || die "reference MySQL container did not start"
  on_tools_host "docker exec -i $name mysql -h127.0.0.1 -uroot reference" < "$SETUP_SQL" \
    || die "setup.sql failed on an empty reference database"
  fingerprint_sql | on_tools_host "docker exec -i $name mysql -h127.0.0.1 -uroot --batch --skip-column-names reference"
}

check_schema() {
  local expected="$DEPLOY_TMP/expected-schema.tsv" actual="$DEPLOY_TMP/actual-schema.tsv" version
  reference_fingerprint > "$expected.raw"
  LC_ALL=C sort "$expected.raw" > "$expected"
  [[ -s "$expected" ]] || die "reference fingerprint is empty"
  version="$(echo 'SELECT VERSION();' | remote_sql)" || die "cannot connect to $SQL_DATABASE on $SQL_HOST"
  log "reading the schema of $SQL_DATABASE on $SQL_HOST (MySQL $version, reference $MYSQL_IMAGE)"
  fingerprint_sql | remote_sql > "$actual.raw"
  LC_ALL=C sort "$actual.raw" > "$actual"
  if diff -u --label "expected (setup.sql)" --label "actual ($SQL_DATABASE)" "$expected" "$actual" >&2; then
    log "schema of $SQL_DATABASE matches setup.sql"
  else
    die "schema of $SQL_DATABASE differs from setup.sql (- expected, + actual). Run 'deploy-database.sh reset' to recreate it."
  fi
}

ensure_writable_target() {
  local protected
  IFS=',' read -r -a protected <<< "${DATABASE_PROTECTED:-}"
  for name in "${protected[@]}"; do
    [[ "$(echo "$name" | xargs)" == "$SQL_DATABASE" ]] && die "$SQL_DATABASE is listed in DATABASE_PROTECTED"
  done
  [[ "${DATABASE_ALLOW_RESET:-}" == "$SQL_DATABASE" ]] \
    || die "set DATABASE_ALLOW_RESET=$SQL_DATABASE in database.env to allow $mode on this database"
}

run_setup() {
  log "running setup.sql on $SQL_DATABASE (it refuses databases that are not empty)"
  remote_sql < "$SETUP_SQL" || die "setup.sql failed; the database must be completely empty"
}

backup_database() {
  local dir="$REPO_ROOT/tmp/work/deployment/database-backups" file
  mkdir -p "$dir"
  file="$dir/$SQL_DATABASE-$(date +%Y%m%d-%H%M%S).sql"
  log "backing up the tables of $SQL_DATABASE to ${file#"$REPO_ROOT"/}"
  if ! (umask 077 && remote_client mysqldump --single-transaction --no-tablespaces --default-character-set=utf8mb4 \
      "$SQL_DATABASE" < /dev/null > "$file"); then
    rm -f "$file"
    die "backup failed; nothing was dropped (use --no-backup to reset without one)"
  fi
}

drop_everything() {
  local objects statements="SET FOREIGN_KEY_CHECKS = 0;" kind name
  objects="$(remote_sql <<'SQL'
SELECT IF(TABLE_TYPE = 'VIEW', 'VIEW', 'TABLE'), TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE();
SELECT ROUTINE_TYPE, ROUTINE_NAME FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = DATABASE();
SQL
)"
  while IFS=$'\t' read -r kind name; do
    [[ -n "$kind" ]] && statements+=" DROP $kind IF EXISTS \`${name//\`/\`\`}\`;"
  done <<< "$objects"
  log "dropping $(grep -c . <<< "$objects" || true) objects from $SQL_DATABASE"
  printf "%s\n" "$statements" | remote_sql || die "dropping objects failed; run reset again after fixing the cause"
}

case "$mode" in
  check)
    check_schema
    ;;
  setup)
    ensure_writable_target
    run_setup
    check_schema
    ;;
  reset)
    ensure_writable_target
    warn "this permanently deletes every table, view and routine in $SQL_DATABASE on $SQL_HOST"
    confirm "Reset database $SQL_DATABASE?" "$SQL_DATABASE"
    if [[ "$backup" == true ]]; then backup_database; fi
    drop_everything
    run_setup
    check_schema
    ;;
esac
