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
