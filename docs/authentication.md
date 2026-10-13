# Workbook registration and login

The login page uses `HomepageUsageControl` to submit `UserCommands` to the backend execution client. `SqlUserCommands` handles registration and login on the server. Registration creates a new account and returns a signed session token; an existing email address or user ID is deliberately rejected. Existing users should request a login token and use the email-and-token login form instead of registering again.

Name edits suggest a student email address until the user enters an address. Subsequent name edits update the suggestion but preserve manually entered or restored addresses. Clearing an entered address re-enables suggestions on the next name edit. Email edits never rewrite the name. New visitors start with empty fields, and saved account details are preserved. Requests trim and normalize email addresses; pasted login tokens are trimmed. Failed login attempts do not automatically send mail: only the explicit token-request button sends an authentication email.

`LoginActionState` disables duplicate submissions while a request is pending and displays translated feedback for validation, duplicate accounts, invalid/expired tokens and backend failures. Registration and login futures wait for the account state transition and propagate failures to the form. Presentation belongs in `homepage/css/workbook/workbook-structure.css`; messages belong in both login language maps.

## Diagnosing failures

- `registration attempt ignored (account created: false / token: None)` in older clients means the backend returned a registration rejection. Inspect the submitted email first: older forms could replace a supplied email with a derived student address, and their default guest address was shared by every visitor. Rejection must not issue a token for an existing account.
- `ExecutionClientPool: all handlers for command login-command failed` is a transport/handler summary. The underlying server exception is needed to identify a particular live incident. One reproducible cause was parsing JDBC token-expiry strings with an ISO-only parser. Database strings use `yyyy-MM-dd HH:mm:ss` with optional fractional seconds; `GenericSqlFunctionality` now accepts that format as well as ISO local timestamps.
- The timestamp correction requires a server deployment. Form and error-reporting corrections require a client deployment. Pushing the source alone does not update a running installation.

## Regression checks

Run `sbt 'server/test' 'client/test' buildJS`, followed by `CHROMIUM_PATH=/usr/bin/chromium node tools/dev/run-workbook-browser-tests.mjs login-authentication`. Server tests read token rows through JDBC doubles without a database dependency. Client tests cover asynchronous feedback and retries. The browser test intercepts backend requests and exercises email preservation, duplicate registration, invalid tokens, transport errors, explicit token requests and successful login without creating accounts or sending real email.
