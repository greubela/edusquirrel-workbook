# Workbook registration and login

The client submits account commands through the authenticated backend execution
transport. Server handlers and token persistence live under
`modules/server/src/main/scala/`; browser forms and account transitions belong to
`modules/client/src/main/scala/it/evadid/homepage/control/`.

Registration creates an account and returns a signed session token. Duplicate
email addresses/user IDs are rejected; existing users request a token and use the
email-and-token login form. Only the explicit token-request action sends an email.
A failed login must not trigger mail or issue a token for an existing account.

The form preserves entered/restored account details, normalizes submitted email
addresses and trims pasted tokens. Pending requests suppress duplicate submissions;
asynchronous failures reach translated feedback rather than disappearing in the
transport layer. Styles belong in `homepage/css/workbook/workbook-structure.css`
and messages in the login language maps.

Saved login restoration accepts only the current schema. Unreadable or obsolete
login records are discarded so startup can show the login form. Other saved
answers and browser storage are left intact. Do not add data migrations during
development; reject unsupported data at its storage boundary.

Account identity and configurable preferences are separate: invalid database
preferences are discarded and replaced by `UserConfig.onlineDefaults`. They must
not prevent login-code delivery or account lookup. No legacy schema is decoded.
Creation and account updates use distinct command names, and updates require the
authenticated user's ID.

## Diagnosing failures

A command-pool error is a transport/handler summary; inspect its underlying server
exception to diagnose an incident. Check submitted account identity, backend
configuration and token expiry before changing client behavior. Token-row parsing
must accept the database timestamp format as well as ISO timestamps. Server and
client fixes require deployment of their respective artifacts; pushing source
alone does not update a running installation. After copying the rebuilt
`artifacts/newest/server.jar` to the server's configured bind-mount path, recreate
the backend container (`docker compose up -d --force-recreate edusquirrel`).
The Pages workflow deploys the browser separately.

## Verification

```sh
sbt 'server/test' 'client/test' buildJS
npm run test:workbooks -- login-authentication saved-user-startup
```

Tests use JDBC doubles and intercepted backend requests. They cover duplicate
registration, token failures, transport errors, explicit token requests and saved
account details without creating real accounts or sending email.

See the [database guide](database/README.md) for account/log table relationships,
the anonymous logging identity and proxy configuration.

### MySQL account queries

The `mysql-accounts` job in `.github/workflows/tests.yml` runs
`sbt 'server / MysqlIntegration / test'` against a temporary MySQL 8.4 service.
It needs no repository secrets: test credentials are derived from the workflow
run, and GitHub removes the service afterward. It runs in parallel with the
ordinary checks and gates the reusable test workflow used before Pages deployment.
Normal `server/test` runs do not require Docker or a database.

The suite exercises the actual registration, update and login handlers plus
`SqlUserCommands` token queries. It checks duplicate accounts, email normalization,
Unicode values, parameter binding, account isolation, token expiry/renewal,
signed-token ownership, and malformed preferences. It never calls SMTP. The DDL
under `modules/server/src/mysqlIntegration/resources/sql/` defines the tested
account-query contract; it does not validate the indexes/types of a deployed
database. Keep that fixture aligned with intentional schema changes.

To run locally with Docker:

```sh
export SQL_HOST=127.0.0.1 SQL_PORT=13306 SQL_DATABASE=edusquirrel_account_test
export SQL_USER=account_test
export SQL_PW="$(openssl rand -hex 16)"
export SERVER_SECRET="$(openssl rand -hex 32)"
docker run --detach --rm --name edusquirrel-account-tests \
  -p 127.0.0.1:13306:3306 \
  -e MYSQL_DATABASE="$SQL_DATABASE" -e MYSQL_USER="$SQL_USER" \
  -e MYSQL_PASSWORD="$SQL_PW" -e MYSQL_RANDOM_ROOT_PASSWORD=yes \
  --health-cmd='mysqladmin ping -h 127.0.0.1 --silent' \
  --health-interval=5s --health-timeout=5s --health-retries=30 mysql:8.4
```

Once `docker inspect --format '{{.State.Health.Status}}' edusquirrel-account-tests`
reports `healthy`, run the sbt command above. Finish with
`docker stop edusquirrel-account-tests`. The suite clears its tables before each
test and refuses to run unless the database name is `edusquirrel_account_test`
and the host is localhost; never point it at a database containing valuable data.
