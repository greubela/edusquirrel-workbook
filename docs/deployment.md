# Deployment

[deploy.sh](../deploy.sh) builds all artifacts and runs the three deployment scripts in
order: database check, backend, website. Each script also runs on its own.

```sh
./deploy.sh                       # build + database check + server + client
./deploy.sh --skip-build          # deploy already built artifacts
./deploy.sh --only=client -- --dry-run
./deploy.sh --database=reset      # recreate the schema (asks for confirmation)
```

Requirements on the deploying machine: Bash, OpenSSH 8.4+ (`ssh`, `ssh-agent`), curl,
Node.js, and sbt/npm for the build. MySQL clients run in Docker on the backend server,
so no local MySQL or Docker is needed (`DATABASE_TOOLS_HOST=local` uses local Docker instead).

## Secrets

`deployment/secrets/` holds four files that Git ignores; copy each `*.example` template
and fill it in. Values are read literally; single-quote values containing `$`, quotes or spaces.
The files are authoritative: exported shell variables (such as development `SQL_*`) are ignored.
`EDUSQUIRREL_SECRETS_DIR` selects another directory, for example for a staging target.

| File | Content |
| --- | --- |
| `local.env` | SSH host/user/key/passphrase and remote directory, FTP login and webspace root, database tool host |
| `database.env` | `SQL_*` connection, `DATABASE_PROTECTED`, `DATABASE_ALLOW_RESET`, reference MySQL image |
| `server.env` | The backend's Compose `.env` (`SERVER_SECRET`, `SQL_*`, `OPENAI_*`, …); `SQL_*` must equal `database.env` |
| `client.env` | Client runtime configuration, published as `deployment/client/.env`; **public**, so credential-like keys are refused |

An encrypted SSH key is unlocked once through a private `ssh-agent` that is stopped on exit.

## Database

[deploy-database.sh](../deployment/database/deploy-database.sh) `check | setup | reset`
targets only `SQL_DATABASE`; [setup.sql](../deployment/database/setup.sql) remains the
single schema source (see the [database guide](database/README.md)).

- `check` (default) builds a reference schema from `setup.sql` in a throwaway MySQL container
  and diffs tables, columns, indexes, foreign keys, routines and the seed row with the live database.
- `setup` runs `setup.sql` on an empty database; its guard refuses any existing table.
- `reset` dumps the tables to ignored `tmp/work/deployment/database-backups/`, drops every
  table, view and routine, then runs `setup` and `check`.

`setup` and `reset` require `DATABASE_ALLOW_RESET` to equal `SQL_DATABASE` and refuse names in
`DATABASE_PROTECTED`, so a database shared with another project is never modified.

## Backend server

[deploy-server.sh](../deployment/server/deploy-server.sh) uploads `artifacts/newest/server.jar`,
[docker-compose.yml](../deployment/server/docker-compose.yml), `server.env` (plus
`SERVER_VERSION` from `git describe`) and [update-docker.sh](../deployment/server/update-docker.sh)
to `SERVER_REMOTE_DIR` as `*.upload` files. `update-docker.sh` keeps the active files as
`*.previous`, activates the uploads, runs `docker compose down` / `up -d` and waits for the
container health check. If the backend stays unhealthy, it keeps the new files as `*.failed`
and restores the previous version; `sh update-docker.sh --rollback` does this manually.
Finally `SERVER_HEALTH_URL` must report the deployed version.

Compose names the project after `SERVER_REMOTE_DIR`. Before the first scripted deployment,
stop a backend started from another directory (`docker compose down` there); otherwise
ports 80/443 are still taken.

## Website

[deploy-client.sh](../deployment/client/deploy-client.sh) assembles `_site/` with
[assemble-pages.mjs](../tools/dev/assemble-pages.mjs) (homepage, resources, newest client/worker,
root redirects), adds `client.env` and uploads to `CLIENT_FTP_ROOT` over FTPS with curl.

[sync-plan.mjs](../deployment/client/sync-plan.mjs) compares SHA-256 hashes with
`.deploy-manifest.sha256`, the manifest of the last deployment stored on the webspace:
only changed files are uploaded, in batches whose progress is recorded so a rerun resumes.
Each batch is verified by remote file size, because FTP servers can confirm transfers they stored
incompletely; mismatches are uploaded again twice before the deployment stops. Only files
that earlier deployments uploaded are deleted. Other webspace content is untouched.

The first deployment has no manifest and uploads everything (about 300 MB). If large parts are
already current on the webspace, mark them with `--trust resources/workbookpdfs` (repeatable);
the next change to such a file is uploaded normally. Use `--dry-run` to list the planned changes.
