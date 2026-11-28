# Backend server

The Play backend in `modules/server/` executes the shared `ExecutionCommand`s that
need server resources: accounts, history storage, mail, LLM requests, submissions
and the SQL editor. [BackendServer](../modules/server/src/main/scala/it/evadid/server/BackendServer.scala)
reads the configuration, checks the backend database and serves the routes.

## Packages

| Package (`it.evadid.server.`) | Responsibility |
| --- | --- |
| root | Entry point, [ServerConfig](../modules/server/src/main/scala/it/evadid/server/ServerConfig.scala), `BlockingExecution`, `SafeFailure` |
| `http` | [HttpApi](../modules/server/src/main/scala/it/evadid/server/http/HttpApi.scala): `/executeCommand` (size limit, CORS), `/health` and the failure envelope |
| `commands` | [BackendCommandHandler](../modules/server/src/main/scala/it/evadid/server/commands/BackendCommandHandler.scala) with its handler table, [CommandAccess](../modules/server/src/main/scala/it/evadid/server/commands/CommandAccess.scala) rules and the background `CommandLogWriter` |
| `account` | Signed login tokens, registration/login/update (`AccountCommands`), account rows and the login mail |
| `events.mail`, `events.llm`, `events.submission` | Outbound SMTP and LLM calls; session submissions with their summary, mails and rate limit |
| `sqlEditor` | Learner SQL on allow-listed teaching databases |
| `database` | `Database` (the only way to open connections, plus transactions), `SqlStatements`, `SqlFailure` |
| `database.schema` | Script-derived schema inspection, explicit setup and the cached `SchemaValidation` |
| `database.history` | `HistoryDatabase` and the `BlobFormat`/`RelationalFormat` storages ([interaction histories](database/interaction-history.md)) |

## Contracts

- **Configuration.** `ServerConfig.fromEnvironment()` is the only environment read. It
  fails at startup without `SERVER_SECRET`, for partial groups (`SQL_*`, `MAIL_EMAIL`/`MAIL_PW`,
  `SQL_EDITOR_USER`/`SQL_EDITOR_PW`) and for non-integer numbers. Tests build configurations from
  explicit maps with `ServerConfig.from`.
- **Access.** Every handler declares a `CommandAccess` rule (`Public`, `SignedIn`, `OwnRecords`,
  `OwnMail`); a rule must match its command's `requiresLogin`. History requests expose
  `DbRequest.ownerUserIds`, so one rule covers every history command.
- **SQL.** Every statement is a constant string; values, optional filters (`(?=0 OR column=?)`) and
  key lists (`JSON_CONTAINS(?, JSON_QUOTE(variable_id))`) are bound parameters. Table names are
  never interpolated, and metadata reads group columns by exact table name instead of using
  table names as LIKE patterns. The SQL editor runs learners' text unprepared by design; the
  allow-list and the restricted `SQL_EDITOR_USER` protect it.
- **Threads.** Blocking JDBC, SMTP and HTTP calls run on `BlockingExecution` (virtual threads);
  parsing and serialization use the global pool. Command logs are written in the background.
- **Schema.** `SchemaValidation` (an `AsyncDataCache`) remembers each valid database and table set;
  failures are retried. At startup an incompatible backend schema stops the server, while an
  unreachable or unconfigured database only warns.
- **Accounts.** Mail addresses are unique and stored in lower case. Registration inserts in one
  transaction and the unique keys decide races; updates never take another account's address.
  Checking a login code never creates one (`getTokenFromDb` vs. `ensureTokenInDb`).
- **Submissions.** `submit-session-event` needs no account, so each mail address may submit once per
  minute and one request address 40 times per minute (a class behind one school network address).
- **Failures.** Diagnostics from JDBC (`SqlFailure`), SMTP and LLM providers (`SafeFailure`) are
  replaced by actionable messages without the original exception, which could echo credentials
  or learner data.

## Verification

`sbt 'server / test'` needs no services. `sbt 'server / MysqlIntegration / test'` runs accounts,
logs, both history layouts, schema checks and the SQL editor against a disposable MySQL 8.4
database ([test guide](core-package-unit-tests.md#run-tests)).
