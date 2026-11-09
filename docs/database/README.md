# Database setup and storage

The authoritative schemas are the executable SQL files under
[`artifacts/database`](../../artifacts/database/). Column definitions, types,
indexes and dependencies are documented there rather than duplicated here.

Run the scripts only against an empty MySQL 8.4 database, in this order:

1. [createUserTables.sql](../../artifacts/database/createUserTables.sql)
2. [createLogTables.sql](../../artifacts/database/createLogTables.sql)
3. [createClassicHistoryTables.sql](../../artifacts/database/createClassicHistoryTables.sql)
4. [createInteractionHistoryTables.sql](../../artifacts/database/createInteractionHistoryTables.sql)

The interaction-history script is also usable on its own in an empty, separate
history database. Its user identifiers are logical references, not foreign keys
to the account database. The log script requires the user script first. The user
script creates the anonymous logging identity `user-unknown`.

## Inspection and initialization

`EnsureDatabaseProperlySetup` reads MySQL/JDBC schema metadata and compares it
with the packaged SQL definitions. Inspection does not read application rows,
run migrations or modify data. Automatic startup inspection and initialization
are currently disabled while the new schema is not live. Explicit setup initializes
only a completely empty database. Any existing table or view prevents automatic creation, including a
partially configured or conflicting schema. Explicit `setupIfEmpty` uses the
same rule. Explicit checks report missing/incompatible structures;
existing databases are never repaired automatically.
An advisory lock coordinates concurrent backend initializers; setup rechecks
emptiness while holding that lock and requires a dedicated auto-commit connection.

All scripts are parsed before the first creation statement. MySQL DDL is not
transactional across tables: a permissions, connection or disk failure during
initial creation can leave a partial schema, which subsequent runs will report
and leave untouched.

The backend reads `SQL_HOST`, `SQL_PORT`, `SQL_DATABASE`, `SQL_USER`, `SQL_PW`
and optional `DB_FORMAT` (MySQL by default). Tests execute the very same packaged
scripts in a disposable database; there is no separate test schema. See the
[account test instructions](../authentication.md#mysql-account-queries).

## History formats and identity

`DatabaseHistoryFormat` describes a list of required tables. Both
`ClassicKeylessJson` and `ClassicJsonWithKey` remain supported. The third option,
`RelationalEvents`, stores answers and feedback independently. Choose the format
in `SyncDestinationBasedOnHistoryDatabase`; configuring it does not convert data
from another format. Its companion defines `defaultDatabaseWithKey`,
`defaultDatabaseKeyless` and `defaultDatabaseV2`; account preferences select these
presets rather than constructing their own defaults. Online accounts deliberately
keep both classic formats active until V2 is deployed and verified.

Numeric database IDs are implementation details and never enter client payloads
or determine answer equality. Graders expose a lazy string `key`: SHA-256 of
canonical constructor content, including grader type. Map/set order and grader
list position do not affect it. Equal configurations share an identity; changed
criteria receive a different identity. Non-product custom graders must override
`identityContent` with stable JSON. Ordinary feedback and entity-specific additional data are
stored separately. See the [storage behavior](interaction-history.md).

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
address on `172.30.0.0/24`. Play uses
`play.http.forwarded.trustedProxies`; Caddy overwrites `X-Forwarded-For` with the
validated direct peer address. Backend port 9000 is not published. If that subnet
conflicts with the host, adjust the subnet, Caddy address and trusted address
together and recreate the services. An additional proxy in front of Caddy needs
explicit Caddy `trusted_proxies` configuration and `{client_ip}` forwarding;
never trust arbitrary incoming forwarding headers or all private address ranges.

## Session connections

`AllUserInfo.credentials: UserCredentials` holds the backend token, single-use
login token, optional `DatabaseConfigWithCredentials` and optional `LlmConfiguration`
only in memory. The user menu's **Session connections** form edits custom
connections. Closing/reloading the page requires entering them again. Browser
preferences use `SavedUserPreferences(user, config)`; exported sessions and
account rows contain no credentials. Online users must sign in again each session.

Database connections have two explicit types under `DatabaseConfiguration`:
`DatabaseConfig` contains format, host, port and database; `DatabaseConfigWithCredentials`
also contains username and password. Both are accepted by sync destinations and
by the required `databaseConfig` field of every database request. There is no
parallel credential field or per-field merging. Public default presets live in
`SyncDestinationDefinition`; both classic formats remain active.

For `DatabaseConfig`, the backend uses credentials from `SQL_*` only if its target
matches the configured server database. `DatabaseConfigWithCredentials` is used
as-is without consulting server defaults. The session form provides the latter
for user-owned databases. Request construction selects session configuration
or the destination definition directly; it does not rewrite account preferences.
User-supplied credentials are removed from account persistence, browser preferences,
exports and command logs, including credentials attached directly to a destination.

Before executing history queries, the backend validates every table required by
the selected history format. Create a custom history database with its corresponding
SQL script first. It is never repaired automatically. Startup inspection remains
disabled while the new schema is not live.

LLM requests carry `llmConfiguration: Option[LlmConfiguration]`. Callers pass the
current session configuration explicitly. `Some(config)` is used as-is, including
an absent API key or token limit; no fields are filled from server settings.
`None` selects the server default: `OPENAI_API_URL` (default
`https://api.openai.com/v1/chat/completions`), `OPENAI_MODEL` (default
`gpt-4o-mini`), `OPENAI_API_KEY` and optional `OPENAI_MAX_TOKENS`.
HTTP redirects are not followed. Custom local services may omit a key.
Database credentials, API keys and prompts are removed from command-log payloads;
provider error bodies are not retained.
