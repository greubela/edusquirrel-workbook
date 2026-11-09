# Application database

The backend uses MySQL through Connector/J. This guide describes the **current**
query/storage contract. The [relational history proposal](interaction-history-proposal.md)
is a separate future design; the current `events` tables still contain history JSON.

## Connection and configuration

`SQL_HOST`, `SQL_PORT`, `SQL_DATABASE`, `SQL_USER` and `SQL_PW` configure the
application database. `DatabaseConfig.readFromEnv` defaults to the `mysql` JDBC
protocol. Partial database definitions inherit missing environment settings,
including `DB_FORMAT` when supplied (otherwise `mysql`). Supply passwords through
deployment configuration, not source. Teaching databases additionally require
the SQL editor allow-list and dedicated credentials; they are not account databases.

## Tables used by the backend

| Table | Columns used by the queries | Purpose |
| --- | --- | --- |
| `user` | `id`, `name`, `mail`, `config` | Account identity and current `UserConfig` JSON. IDs are primary keys; email is normalized to lowercase by account queries. |
| `loginTokens` | `userId`, `token`, `tokenExpires` | One login-code row per account, upserted by `userId`; expiry must retain the required timestamp precision. |
| `events` | `eventid`, `programid`, `scenarioid`, `userid`, `timestamp`, `serializeddata` | History snapshots with the interaction key inside the JSON wrapper. |
| `eventsWithKey` | Same columns plus `eventkey` | Keyed snapshots; the upsert requires uniqueness for the complete `(programid, scenarioid, userid, eventkey)` scope. |
| `commandLog` | See below | General execution-command audit events. |
| `syncedLog` | Identical to `commandLog` | Fetch/store/delete history command audit events, separate from saved student work. |

`serializeddata` is the current `HistoryJson.stored` envelope:
`{"keyForSerialisation":"...","fullHistory":{...}}`. `fullHistory` contains
`historyStates`, `historyGrading` and `historyScaffolding`. In `eventsWithKey`,
`eventkey` also stores the interaction key explicitly.

Account queries are in `SqlUserCommands.scala`; history queries in
`commandHandler/sql/sync/`; log queries in `SqlLogCommands.scala`, all under
`modules/server/src/main/scala/it/evadid/server/`. Table/column names are case
sensitive on typical Linux MySQL installations. The log schema below follows the
provided production table. Other deployed column sizes, indexes and foreign keys
must be checked with `SHOW CREATE TABLE`; the repository's test fixture is a query
contract, not a complete dump of the live database.

### Command log schema

Both log tables have these columns:

| Column | Type / meaning |
| --- | --- |
| `id` | `BIGINT`, primary key, auto-increment |
| `timestamp` | `TIMESTAMP`, server command receipt time |
| `origin` | `VARCHAR(128)`, numeric client address; avoids blocking reverse-DNS lookups |
| `type` | `VARCHAR(128)`, execution-command name |
| `payload` | `LONGTEXT`, JSON command envelope after its configured privacy filter |
| `userid` | `VARCHAR(128)`, FK to `user.id` |
| `ipAddress` | `VARCHAR(45)`, numeric IPv4 or IPv6 client address |

The supplied production schema has `ipAddress VARCHAR(16)`; widen it before
deploying IPv6 logging. There is no `tokenInfo` column in this schema. The logger
stores the verified token's **user ID**, not its signed token or serialized identity.
Existing command-specific payload privacy filters remain active.
Login, registration and account-update payloads retain only identity metadata;
submitted login codes, signed tokens and account connection settings are omitted.

Unauthenticated requests use `user-unknown` (`User.Anonymous`). That row must
exist to satisfy the log foreign keys. It is only a logging identity: it does not
authorize account-only execution commands. An existing anonymous row's settings
are not read to write a log. Invalid account settings elsewhere are replaced by
current defaults on lookup; unsupported history records are not migrated.

### Applying the logging schema change

Run [command-logging.sql](../../deployment/server/sql/command-logging.sql) **once**
against the application database before deploying this backend. It widens the IP
column, creates `syncedLog` from the existing `commandLog`, adds its user foreign
key, and inserts the anonymous identity only if absent. `CREATE TABLE ... LIKE`
copies columns/indexes, but does not copy foreign keys. Existing account rows and
history formats are not rewritten. The script assumes the current `commandLog`
already has `userid` referencing `user.id`, as in the provided schema.

### Configuring log destinations

Every `ExecutionCommandFactory` has `storeLogIntoTable: Option[DatabaseTable]`:

```scala
ExecutionCommandFactory[Request, Response]("my-command",
  storeLogIntoTable = Some(DatabaseTable("myCommandLog")))
```

The default is `Some(DatabaseTable.commandLog)`. All three `SQLCommands` sync
definitions explicitly select `DatabaseTable.syncedLog`. Set `None` to disable
database logging for a definition. A custom table must have the same writable
log columns; its `inDatabase` can override connection settings. Table identifiers
are validated, and row values use prepared-statement parameters.

Register new command definitions in `ExecutionCommandFactory.knownCommands`.
The backend resolves logging policy and privacy filters from this catalogue;
clients cannot choose a destination in their request JSON. Unknown command names
use `commandLog`. Logging is best-effort: a database failure produces a server
warning and does not prevent the requested command from reaching its handler.

## Client IP addresses behind Caddy

The backend logs `request.connection.remoteAddress`, as resolved by Play's
forwarded-header handling. Do not parse an arbitrary incoming `X-Forwarded-For`
header directly. The correct Play setting is
`play.http.forwarded.trustedProxies`, not `play.http.trustedProxies`.

The supplied Compose deployment gives Caddy `172.30.0.2` on the dedicated
`172.30.0.0/24` network. Play trusts that exact address and loopback; backend port
9000 is not published. Caddy overwrites `X-Forwarded-For` with its direct client's
address. This makes direct header spoofing ineffective. `X-Real-IP` is also sent,
but Play uses `X-Forwarded-For` for this configuration.

If the subnet conflicts with the host's networks, change the subnet, Caddy's
static address and Play's trusted address together. Recreate the Compose services
after changing network configuration. If another trusted proxy sits in front of
Caddy, configure Caddy's `trusted_proxies` for its exact addresses/CIDRs and use
its validated client address (`{client_ip}`) for the upstream header. Without that
explicit trust configuration the log correctly identifies the immediate upstream
peer, not an unverified address asserted by it. Never trust all Internet addresses
or all private subnets merely to make forwarding work.

## Verification

`server/test` includes real HTTP checks for trusted forwarding and rejection of
spoofed headers from untrusted peers. `server / MysqlIntegration / test` exercises
account workflows and inserts actual log rows, including anonymous/authenticated
users, sync routing, IPv6, privacy filtering and disabled logging. It also executes
the deployment SQL against a fixture of the supplied pre-change log schema.
See [local/CI test setup](../authentication.md#mysql-account-queries).
