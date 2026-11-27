#!/usr/bin/env bash
# Assembles the site (homepage, resources, client/worker artifacts, root redirects) with
# tools/dev/assemble-pages.mjs, adds secrets/client.env as deployment/client/.env and uploads
# the files that changed since the last deployment to CLIENT_FTP_ROOT over FTP(S).
#
# Options:
#   --dry-run          Show what would be uploaded or deleted without changing the webspace
#   --trust PREFIX     On a first deployment, treat files under PREFIX (e.g. resources/workbookpdfs)
#                      as already present instead of uploading them; repeatable
#   --skip-assemble    Reuse the existing _site/ directory
source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"
LOG_PREFIX=client

dry_run=false assemble=true trust=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) dry_run=true ;;
    --skip-assemble) assemble=false ;;
    --trust) [[ $# -ge 2 ]] || die "--trust needs a path prefix"; trust+=(--trust "$2"); shift ;;
    *) die "unknown argument: $1" ;;
  esac
  shift
done

load_env local.env
require_vars CLIENT_FTP_HOST CLIENT_FTP_USER CLIENT_FTP_PASSWORD
require_cmd node curl
CLIENT_ENV="$SECRETS_DIR/client.env"
SITE="$REPO_ROOT/_site"

# deployment/client/.env is publicly downloadable from the website.
while IFS= read -r -d '' key && IFS= read -r -d '' _; do
  if [[ "$(printf '%s' "$key" | tr '[:lower:]' '[:upper:]')" =~ (PASS|PW|SECRET|TOKEN|KEY|CREDENTIAL) ]]; then
    die "client.env is public on the website and must not contain $key"
  fi
done < <(env_file_entries "$CLIENT_ENV")

if [[ "$assemble" == true ]]; then
  log "assembling the site"
  (cd "$REPO_ROOT" && node tools/dev/assemble-pages.mjs) >&2
fi
[[ -f "$SITE/homepage/index.html" ]] || die "missing _site/; run without --skip-assemble"
mkdir -p "$SITE/deployment/client"
cp "$CLIENT_ENV" "$SITE/deployment/client/.env"

# Credentials go through a private curl config file, never the command line.
curl_auth="$DEPLOY_TMP/auth.curl"
escape() { local value=${1//\\/\\\\}; printf '%s' "${value//\"/\\\"}"; }
{
  printf 'user = "%s:%s"\n' "$(escape "$CLIENT_FTP_USER")" "$(escape "$CLIENT_FTP_PASSWORD")"
  printf 'silent\nshow-error\nftp-create-dirs\nconnect-timeout = 30\nretry = 2\n'
  if [[ "${CLIENT_FTP_TLS:-true}" == true ]]; then printf 'ssl-reqd\n'; fi
} > "$curl_auth"
chmod 600 "$curl_auth"
ftp() { curl --config "$curl_auth" "$@"; }

base_url="ftp://$CLIENT_FTP_HOST${CLIENT_FTP_PORT:+:$CLIENT_FTP_PORT}"
# Relative to the FTP login directory; surrounding slashes are dropped.
root="${CLIENT_FTP_ROOT:-EvaHomepage/edusquirrel}" root="${root#/}" root="${root%/}"
plan_dir="$DEPLOY_TMP/plan"
mkdir -p "$plan_dir"

log "reading the manifest of the last deployment from $CLIENT_FTP_HOST/$root"
previous="$plan_dir/previous.sha256"
manifest_url="$base_url/$root/.deploy-manifest.sha256"
if ! ftp --output "$previous" "$manifest_url" 2> "$plan_dir/manifest-error.txt"; then
  # curl exit 78: the remote file does not exist (first deployment).
  grep -q '(78)' "$plan_dir/manifest-error.txt" || die "cannot read $manifest_url: $(cat "$plan_dir/manifest-error.txt")"
  rm -f "$previous"
fi

node "$DEPLOY_DIR/client/sync-plan.mjs" "$SITE" "$previous" "$plan_dir" "$base_url" "$root" "${trust[@]+"${trust[@]}"}"
summary() { node -e 'const s = require(process.argv[1]); console.log(eval(process.argv[2]))' "$plan_dir/summary.json" "$1"; }
uploads="$(summary 's.uploads')" deletes="$(summary 's.deletes')"
if [[ "$(summary 's.firstDeployment')" == true ]]; then
  warn "no deployment manifest on the webspace: this deployment uploads every file not covered by --trust"
fi
log "$(summary 's.files') files assembled; $uploads to upload, $deletes to delete"

if [[ "$dry_run" == true ]]; then
  summary 's.uploadList.map(p => "  upload " + p).concat(s.deleteList.map(p => "  delete " + p)).join("\n")' >&2
  log "dry run: webspace unchanged"
  exit 0
fi

shopt -s nullglob
batches=("$plan_dir"/upload-*.curl)
for batch in "${batches[@]}"; do
  number="${batch##*/upload-}" number="${number%.curl}"
  log "uploading batch $((10#$number)) of ${#batches[@]}"
  ftp --fail-early --config "$batch" || die "upload failed; rerun to continue (finished batches are recorded)"
  # Record progress so a rerun skips the batches that already arrived.
  ftp --upload-file "$plan_dir/progress-$number.sha256" "$manifest_url" || die "could not record upload progress"
done
if [[ "$deletes" -gt 0 ]]; then
  log "deleting $deletes files that earlier deployments uploaded"
  ftp --config "$plan_dir/delete.curl" --list-only --output /dev/null "$base_url/" || warn "some deletions failed"
fi
ftp --upload-file "$plan_dir/manifest.sha256" "$manifest_url" || die "could not upload the deployment manifest"
log "client deployed to $CLIENT_FTP_HOST/$root"
