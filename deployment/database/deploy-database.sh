#!/usr/bin/env bash
# Checks or (re)creates the EduSquirrel schema in the dedicated MySQL database.
#
#   deploy-database.sh check   Compare the live schema with setup.sql (default; changes nothing)
#   deploy-database.sh setup   Create the tables of setup.sql, then check. If some of them already
#                              exist, ask [y/N] whether to back them up, drop them and create them again.
#
# The target is SQL_DATABASE from server.env, the backend's own database; nothing else is touched.
# Options: --yes (answer yes), --no-backup (drop without a dump first).
# MySQL clients run in Docker on DATABASE_TOOLS_HOST: "server" (the backend host via SSH,
# default) or "local". The reference schema for "check" is built from setup.sql in a
# throwaway MySQL container on the same host, so no database other than SQL_DATABASE is touched.
# shellcheck source=SCRIPTDIR/../lib/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"
LOG_PREFIX=database

mode=check backup=true
for arg in "$@"; do
  case "$arg" in
    check|setup) mode=$arg ;;
    --yes) DEPLOY_ASSUME_YES=true ;;
    --no-backup) backup=false ;;
    *) die "unknown argument: $arg" ;;
  esac
done

load_env local.env
# The connection comes from server.env, the backend's own configuration; nothing else is read from it.
env_file_entries "$SECRETS_DIR/server.env" > /dev/null
for key in SQL_HOST SQL_PORT SQL_DATABASE SQL_USER SQL_PW; do
  if value="$(env_file_value "$SECRETS_DIR/server.env" "$key")"; then export "$key=$value"; fi
done
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
  remote_client mysql --batch --skip-column-names --default-character-set=utf8mb4 "$SQL_DATABASE"
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
  fingerprint_sql | on_tools_host "docker exec -i $name mysql -h127.0.0.1 -uroot --batch --skip-column-names reference" \
    || die "cannot read the reference schema"
}

check_schema() {
  local expected="$DEPLOY_TMP/expected-schema.tsv" actual="$DEPLOY_TMP/actual-schema.tsv" version
  reference_fingerprint > "$expected.raw"
  LC_ALL=C sort "$expected.raw" > "$expected"
  [[ -s "$expected" ]] || die "reference fingerprint is empty"
  version="$(echo 'SELECT VERSION();' | remote_sql)" || die "cannot connect to $SQL_DATABASE on $SQL_HOST"
  log "reading the schema of $SQL_DATABASE on $SQL_HOST (MySQL $version, reference $MYSQL_IMAGE)"
  fingerprint_sql | remote_sql > "$actual.raw" || die "cannot read the schema of $SQL_DATABASE"
  LC_ALL=C sort "$actual.raw" > "$actual"
  if diff -u --label "expected (setup.sql)" --label "actual ($SQL_DATABASE)" "$expected" "$actual" >&2; then
    log "schema of $SQL_DATABASE matches setup.sql"
  else
    die "schema of $SQL_DATABASE differs from setup.sql (- expected, + actual). Run 'deploy-database.sh setup' to recreate it."
  fi
}

# The tables setup.sql creates, one per line.
# shellcheck disable=SC2016  # The backticks are part of the sed pattern.
setup_tables() {
  sed -n '/^-- BEGIN APPLICATION TABLES/,$p' "$SETUP_SQL" | sed -nE 's/^CREATE TABLE `([^`]+)`.*/\1/p'
}

run_setup() {
  log "running setup.sql on $SQL_DATABASE"
  remote_sql < "$SETUP_SQL" || die "setup.sql failed (see above)"
}

backup_tables() {
  local dir="$REPO_ROOT/tmp/work/deployment/database-backups" file
  mkdir -p "$dir"
  file="$dir/$SQL_DATABASE-$(date +%Y%m%d-%H%M%S).sql"
  log "backing up the tables to ${file#"$REPO_ROOT"/}"
  if ! (umask 077 && remote_client mysqldump --single-transaction --no-tablespaces --default-character-set=utf8mb4 \
      "$SQL_DATABASE" "$@" < /dev/null > "$file"); then
    rm -f "$file"
    die "backup failed; nothing was dropped (use --no-backup to drop without one)"
  fi
}

# Creates the schema. Existing tables of setup.sql are dropped only after a yes; other tables
# are never touched, and since setup.sql refuses a non-empty database they stop the setup first.
setup_schema() {
  local existing kind name drop=() drop_sql="SET FOREIGN_KEY_CHECKS = 0;" others=()
  existing="$(echo "SELECT TABLE_TYPE, TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE();" \
    | remote_sql)" || die "cannot connect to $SQL_DATABASE on $SQL_HOST"
  while IFS=$'\t' read -r kind name; do
    [[ -n "$name" ]] || continue
    if grep -qxF "$name" <(setup_tables); then
      drop+=("$name")
      if [[ "$kind" == VIEW ]]; then kind=VIEW; else kind=TABLE; fi
      drop_sql+=" DROP $kind IF EXISTS \`${name//\`/\`\`}\`;"
    else
      others+=("$name")
    fi
  done <<< "$existing"

  if [[ ${#others[@]} -gt 0 ]]; then
    die "$SQL_DATABASE contains tables that setup.sql does not create: ${others[*]}. Remove them yourself; nothing was changed."
  fi
  if [[ ${#drop[@]} -gt 0 ]]; then
    warn "$SQL_DATABASE on $SQL_HOST is not empty. These tables would be dropped with all their data and created again:"
    printf '  %s\n' "${drop[@]}" >&2
    ask_yes_no "Drop and re-create these ${#drop[@]} tables?" || die "aborted; nothing was changed"
    if [[ "$backup" == true ]]; then backup_tables "${drop[@]}"; fi
    log "dropping ${#drop[@]} tables from $SQL_DATABASE"
    printf '%s\n' "$drop_sql" | remote_sql || die "dropping the tables failed (see above)"
  fi
  run_setup
}

case "$mode" in
  check)
    check_schema
    ;;
  setup)
    setup_schema
    check_schema
    ;;
esac
