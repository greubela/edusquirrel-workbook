# Database setup and connections

[setup.sql](../../artifacts/database/setup.sql) is the single schema source for
MySQL 8.4: account/login-token tables, command/sync logs, relational event tables,
blob histories and the anonymous logging identity. Both history layouts use one
database. See [history storage](interaction-history.md) for their architecture.

## Create an empty database

Set the `CREATE DATABASE` and `USE` statements at the top of the script to your
chosen database name (default: `edusquirrel`), then run:

```sh
mysql --host=localhost --user=YOUR_USER --password < artifacts/database/setup.sql
```

For hosted accounts without database-creation privileges, omit those two header
statements and select an existing empty database:

```sh
mysql --host=HOST --user=USER --password --database=YOUR_EMPTY_DATABASE < artifacts/database/setup.sql
```

The header's `USE` determines the target when present; `--database` does not override
it. Database selection is explicit because MySQL's prepared-statement protocol does
not support a configurable `USE` statement.

| Target state | Setup behavior |
| --- | --- |
| No database / empty database | Creates the database if permitted, then all application tables and the seed row |
| Any table or view exists | Fails `setup_requires_empty_database` before application DDL, regardless of names, structure or existing rows |
| Partial schema from an interrupted run | Refuses to continue; it does not repair or complete existing schemas |

Run as a normal batch **without `--force` or an importer option that continues
after errors**. The guard leaves existing tables and data untouched when execution
stops on its error. The script never alters, drops or migrates existing tables.
MySQL DDL is not transactional across tables: a permissions, connection or disk
failure during initial creation can leave a partial schema.

For disposable development data, use a new database name or remove the dedicated
database first. Deleting rows is insufficient. For example, this permanently
removes a development database and all its data:

```sql
DROP DATABASE IF EXISTS edusquirrel_dev;
```

Then set **both** header statements in `setup.sql` to `edusquirrel_dev` and rerun
the import. On a live database, keep the guard and stop-on-error behavior; any
schema change requires a separately reviewed operation, not this setup script.

## Inspection and initialization

| Class | Responsibility |
| --- | --- |
| [DatabaseSqlScripts](../../modules/server/src/main/scala/it/evadid/server/database/DatabaseSqlScripts.scala) | Parses the packaged schema and exposes application table definitions |
| [EnsureDatabaseProperlySetup](../../modules/server/src/main/scala/it/evadid/server/database/EnsureDatabaseProperlySetup.scala) | Inspects metadata, reports missing/incompatible structures, and explicitly initializes empty databases |
| [DatabaseRequestConnection](../../modules/server/src/main/scala/it/evadid/server/database/DatabaseRequestConnection.scala) | Resolves connection configuration and verifies the selected layout's required schema |

Inspection reads metadata, not application rows. `setupIfEmpty` requires a dedicated
auto-commit connection, parses all definitions before creation and rechecks emptiness
under an advisory lock. It reads the application section of the same script without
creating or selecting another database. Startup/deployment does not automatically
initialize or repair databases. Existing tables/views prevent initialization.

`DatabaseHistoryFormat` selects `BlobFormat` or `RelationalFormat` and exposes
`DatabaseTable`/`DatabaseColumn`/`MySqlType` metadata. Configuring another layout
changes the destination; it does not migrate existing data. History user IDs are
logical references; account log/token tables reference the account `user` table.

## Connection configuration

Backend defaults come from `SQL_HOST`, `SQL_PORT`, `SQL_DATABASE`, `SQL_USER`,
`SQL_PW` and optional `DB_FORMAT` (default `mysql`). History requests carry one
`DatabaseConfiguration`, resolved as follows:

| Configuration | Resolution |
| --- | --- |
| `BackendDatabase` | The receiving backend's environment configuration; default presets contain no server credentials |
| `DatabaseConfig` | Public address fields; uses backend credentials only when the target matches backend defaults |
| `DatabaseConfigWithCredentials` | Explicit address and credentials, used as supplied without per-field merging or consulting defaults |

`SyncDestinationDefinition` declares destinations; `UserConfig.defaultSyncDestinations`
enables both backend layouts with `SYNC_EVERYTHING` and browser IndexedDB with
`SYNC_MAJOR`. The [synchronization guide](interaction-history.md) explains transmission
versus retention and per-context batching.

`AllUserInfo.credentials: UserCredentials` holds session tokens, an optional database
connection and optional `LlmConfiguration`. The user menu's **Session connections**
form edits these; request construction chooses session configuration or the destination
definition directly. Credentials survive reload in the browser's IndexedDB login cache,
with backend-token validation, and logout removes them. Account rows, localStorage
`SavedUserPreferences`, exports and command logs exclude credentials, including
credentials attached to a destination. See [authentication](../authentication.md).

LLM requests carry `llmConfiguration: Option[LlmConfiguration]`. `Some(config)` is
used as-is, including an absent API key or token limit. `None` selects server defaults:
`OPENAI_API_URL` (default `https://api.openai.com/v1/chat/completions`), `OPENAI_MODEL`
(default `gpt-4o-mini`), `OPENAI_API_KEY` and optional `OPENAI_MAX_TOKENS`. Redirects
are not followed. Local services may omit an API key; provider error bodies, API keys
and prompts are excluded from command logs.

## Logging and proxy addresses

`ExecutionCommandFactory.storeLogIntoTable` defaults to `commandLog`; history commands
select `syncedLog`, and `None` disables database logging. Custom log tables need the
same writable columns as the script's log tables. Logging failure warns without
preventing history commands from executing. Anonymous logging grants no authorization.

Both `origin` and `ipAddress` use Play's resolved connection address. In the supplied
Compose deployment, Caddy is the public entry point and the backend port is not
published. Play trusts loopback and Caddy at `172.30.0.2`; automatic container addresses
start at `172.30.0.128/25` within `172.30.0.0/24`, keeping Caddy's address reserved.
If that subnet conflicts locally, update the network, Caddy address and trusted-proxy
configuration together, then recreate services with `docker compose down` / `up -d`.

Caddy replaces client forwarding headers with its validated peer address. An additional
proxy/NAT needs an explicit trust arrangement at that hop; never trust arbitrary
`X-Forwarded-For` values or all private networks. HTTP integration tests cover trusted
IPv4/IPv6 forwarding and spoof rejection.

## Failure diagnostics

Parsing errors identify the request/history entity and include a bounded, privacy-filtered
preview and parser reason. Command pools report attempted handlers and their last failure.
`SqlFailure` supplies an operation, SQLState explanation and vendor error code. Original
JDBC errors stay in server logs; their cause chains are omitted from browser responses
because they can contain bound values or credentials. Unsupported formats and incompatible
schemas fail explicitly, without fallback readers or automatic repair.
