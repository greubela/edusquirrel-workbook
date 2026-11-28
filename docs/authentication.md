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

Saved localStorage preferences contain only `user` and `config`. The current
`AllUserInfo`, including backend/login tokens and custom database/LLM credentials,
is cached under `edusquirrel-session` in IndexedDB (`EvaDidCacheDb`, `cache`).
Reload validates the cached backend token and restores custom connections; logout
removes that cache record. Credentials remain excluded from server account
preferences, localStorage and exported sessions. The existing short-lived server
login-code verification workflow is unchanged. Browser storage failures do not
prevent login or logout from updating the in-memory session.

Startup restores a local profile or signs in an online account whose cached token the
backend accepts. Otherwise (no usable token, a refused or unreachable login) the login
page shows, pre-filled with the last recognized name and email
(`UserSessionControl.loginIdentity`), and the cached login is cleared. "Start locally"
without a name creates a local user named `Anonymous` with mail `anonymous@evadid.it`
(`User.createAnonymous`); like a named local profile it is saved, restored on reload
until an explicit logout, and pre-fills the login page. Local (offline) accounts are
identified only by `UserConfig.isOnlineAccount = false`, never by name or mail. An online account loaded from a session file is never
continued as a local profile.

Saved preference restoration accepts only the current schema. Unreadable or obsolete
login records are discarded so startup can show the login form. Other saved
answers and browser storage are left intact. Do not add data migrations during
development; reject unsupported data at its storage boundary.

Unknown email addresses are reported as an unknown account without attempting SMTP.
Delivery/transport failures display a server-error message and allow retry; they do
not claim that the email address is wrong. Missing/invalid stored login codes are
discarded, and requesting a code creates a new one. Token expiry rows accept only the canonical nine-digit UTC text.

Account identity and configurable preferences are separate: invalid database
preferences are discarded and replaced by `UserConfig.defaults(isLocalAccount = false)`. They must
not prevent login-code delivery or account lookup.
Creation and account updates use distinct command names, and updates require the
authenticated user's ID. All account operations run through
`UserAccountDatabase.execute`, which owns one connection and closes it on success
or failure. Registration checks and insertion share that scope; SMTP delivery
runs after database work has released its connection.

## Diagnosing failures

SMTP failures distinguish rejected authentication/delivery, DNS, connection,
TLS and socket timeouts without echoing message contents or login codes.
Mail delivery uses 10-second connection and 15-second read/write timeouts.
Browser request errors identify the command and distinguish the 60-second
backend timeout from browser cancellation or a network failure.

A command-pool error is a transport/handler summary; inspect its underlying server
exception to diagnose an incident. Check submitted account identity, backend
configuration and token expiry before changing client behavior. Fixes reach a running
installation only through [deployment](deployment.md); pushing source alone changes nothing.

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

`sbt 'server / MysqlIntegration / test'` exercises the real registration, update and login
handlers and `UserAccountRows` token queries against MySQL 8.4: duplicate accounts, email
normalization, Unicode, parameter binding, account isolation, token expiry/renewal,
signed-token ownership and malformed preferences. It never calls SMTP and uses the
authoritative `deployment/database/setup.sql`. CI runs it against a temporary service; to run
it locally, follow the [test guide](core-package-unit-tests.md#run-tests). Normal `server/test`
runs need no database.
