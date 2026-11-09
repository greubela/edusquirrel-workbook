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
run migrations or modify data. Startup initializes only a completely empty
database. Any existing table or view prevents automatic creation, including a
partially configured or conflicting schema. Explicit `setupIfEmpty` uses the
same rule. Missing/incompatible structures are reported for operator review;
existing databases are never repaired automatically.

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
from another format.

Numeric database IDs are implementation details and never enter client payloads
or determine answer equality. Graders expose a string `key` composed of the
variable sync key and the zero-based position in its configured grader list.
Keep list order stable when maintaining an exercise; reordering graders changes
their identities. Ordinary feedback and entity-specific additional data are
stored separately. See the [storage behavior](interaction-history-proposal.md).

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
