# Database setup and storage

The authoritative schema is [setup.sql](../../artifacts/database/setup.sql).
It contains the column definitions, indexes, dependencies and initial account row.

Run [setup.sql](../../artifacts/database/setup.sql) once for a new MySQL 8.4
installation. It creates the database, account/login-token tables, command/sync
logs, relational event tables and blob-history table, and seeds the anonymous
logging identity. There is one maintained schema source.

```sh
mysql --host=localhost --user=YOUR_USER --password < artifacts/database/setup.sql
```

The script checks for any existing table or view before creating application
tables. A nonempty database fails the `setup_requires_empty_database` assertion;
no existing tables are altered or dropped. Run as a normal batch without
`--force` or an importer setting that continues after errors.

The default database is `edusquirrel`. To choose another name, edit the
`CREATE DATABASE` and `USE` statements at the top. MySQL does not support `USE`
through its prepared-statement protocol, so a SQL variable cannot select it
portably. Hosted accounts can omit those two statements and run:

```sh
mysql --host=HOST --user=USER --password --database=YOUR_EMPTY_DATABASE < artifacts/database/setup.sql
```

The backend's empty-database initializer reads the same script after its
`BEGIN APPLICATION TABLES` marker. It uses the configured connection's database,
without requiring database-creation privileges or changing databases. History
user identifiers are logical references; only account log/token tables have
foreign keys to `user`.

## Inspection and initialization

`EnsureDatabaseProperlySetup` reads MySQL/JDBC schema metadata and compares it
with the packaged setup script. Inspection does not read application rows,
run migrations or modify data. Automatic startup inspection and initialization
remain disabled; deployment does not automatically change existing databases.
Explicit setup initializes only a completely empty database. Any existing table or view prevents automatic creation, including a
partially configured or conflicting schema. Explicit `setupIfEmpty` uses the
same rule. Explicit checks report missing/incompatible structures;
existing databases are never repaired automatically.
An advisory lock coordinates concurrent backend initializers; setup rechecks
emptiness while holding that lock and requires a dedicated auto-commit connection.

All table definitions are parsed before the first creation statement. MySQL DDL is not
transactional across tables: a permissions, connection or disk failure during
initial creation can leave a partial schema, which subsequent runs will report
and leave untouched.

The backend reads `SQL_HOST`, `SQL_PORT`, `SQL_DATABASE`, `SQL_USER`, `SQL_PW`
and optional `DB_FORMAT` (MySQL by default). Tests execute the same packaged
script in a disposable database; there is no separate test schema. See the
[account test instructions](../authentication.md#mysql-account-queries).

## History formats and identity

`DatabaseHistoryFormat` selects `BlobFormat` or `RelationalFormat` and lists its
required tables. Configure it in `SyncDestinationBasedOnHistoryDatabase`;
`defaultDatabase` selects relational storage. Both use the same typed event history
and reject format changes for an existing sync context. Configuring a destination
does not migrate data from another layout.

Format names describe state and feedback codecs independently of workbook IDs.
Numeric database IDs remain internal. Graders use a SHA-256 key derived from their
canonical constructor content: equivalent configurations share an identity and
changed criteria receive a new one. See [storage behavior](interaction-history.md)
and [interaction formats](../interaction-format.md).

## Command logging and proxy addresses

`ExecutionCommandFactory.storeLogIntoTable` defaults to `commandLog`; history
sync commands select `syncedLog`; `None` disables database logging. Custom tables
need the same writable log structure as the referenced SQL script. Register new
commands in `ExecutionCommandFactory.knownCommands`; routing is server-owned and
cannot be overridden through a request payload. Account logs retain identity
metadata, not submitted tokens or connection settings. Logging failures warn
without preventing handler execution. Anonymous logging does not grant access
to authenticated commands.

The Compose deployment trusts only loopback and Caddy's fixed `172.30.0.2`
address on `172.30.0.0/24`. Automatic container addresses are allocated from
`172.30.0.128/25`, reserving the lower half for static addresses. Without this
separation, the backend can acquire `.2` before Caddy starts. Network changes
require `docker compose down` followed by `docker compose up -d` so Docker
recreates the network. Play uses
`play.http.forwarded.trustedProxies`; Caddy overwrites `X-Forwarded-For` with the
validated direct peer address. Backend port 9000 is not published. If that subnet
conflicts with the host, adjust the subnet, Caddy address and trusted address
together and recreate the services. An additional proxy in front of Caddy needs
explicit Caddy `trusted_proxies` configuration and `{client_ip}` forwarding;
never trust arbitrary incoming forwarding headers or all private address ranges.

## Session connections

`AllUserInfo.credentials: UserCredentials` holds the backend token, single-use
login token, optional `DatabaseConfigWithCredentials` and optional `LlmConfiguration`.
The user menu's **Session connections** form edits custom connections. The full
current profile is cached in browser IndexedDB and restored after reload, with
backend-token validation. Logout removes the cached credentials. localStorage
preferences use `SavedUserPreferences(user, config)`; exported sessions and
account rows contain no credentials.

Database connections have two explicit types under `DatabaseConfiguration`:
`DatabaseConfig` contains format, host, port and database; `DatabaseConfigWithCredentials`
also contains username and password. Both are accepted by sync destinations and
by the required `databaseConfig` field of every database request. There is no
parallel credential field or per-field merging. Public default presets live in
`SyncDestinationDefinition`; both current database formats are supported.

For `DatabaseConfig`, the backend uses credentials from `SQL_*` only if its target
matches the configured server database. `DatabaseConfigWithCredentials` is used
as-is without consulting server defaults. The session form provides the latter
for user-owned databases. Request construction selects session configuration
or the destination definition directly; it does not rewrite account preferences.
User-supplied credentials are removed from account persistence, localStorage preferences,
exports and command logs, including credentials attached directly to a destination.

History requests validate their selected layout's required tables against the
packaged schema. Malformed requests and unsupported formats fail explicitly.
Create custom databases with the current SQL scripts; existing schemas are never
repaired automatically. Local browser storage excludes malformed records individually.

If `syncedLog` is missing in an existing deployment, execute its `CREATE TABLE`
statement from [setup.sql](../../artifacts/database/setup.sql)
manually, after ensuring the referenced user table exists. Do not rerun the full
script over existing tables. Logging failures are warnings and do not prevent
history requests from executing.

LLM requests carry `llmConfiguration: Option[LlmConfiguration]`. Callers pass the
current session configuration explicitly. `Some(config)` is used as-is, including
an absent API key or token limit; no fields are filled from server settings.
`None` selects the server default: `OPENAI_API_URL` (default
`https://api.openai.com/v1/chat/completions`), `OPENAI_MODEL` (default
`gpt-4o-mini`), `OPENAI_API_KEY` and optional `OPENAI_MAX_TOKENS`.
HTTP redirects are not followed. Custom local services may omit a key.
Database credentials, API keys and prompts are removed from command-log payloads;
provider error bodies are not retained.

Client IPs are written to `origin` and `ipAddress` in both `commandLog` and
`syncedLog` from Play's resolved connection address. The MySQL HTTP integration
tests verify trusted IPv4/IPv6 forwarding and reject spoofed forwarding headers
from untrusted peers. With the supplied Compose configuration, Caddy must be the
public entry point: it forwards its socket peer's address and Play trusts only
Caddy and loopback. If another proxy or NAT hides the original address before
Caddy, that address cannot be recovered without a trusted forwarding arrangement
at that hop; do not accept arbitrary client-supplied `X-Forwarded-For` values.

## Failure diagnostics

Command pools report the number of attempted handlers and retain the last
handler's cause chain. Parsing boundaries name the request/history/feedback
entity, show a bounded input preview, and include the underlying parser reason.
Malformed stored records are still discarded individually; these diagnostics do
not enable legacy schema support.

`SqlFailure` gives account and history failures an operation, an actionable
SQLState explanation and the vendor error code. Original JDBC exceptions remain
in server logs; they are deliberately excluded from transport cause chains
because vendor messages can contain bound values or credentials. Request parsing
uses each command's privacy filter for previews and nested error messages.
