# Interaction history storage behavior

The implemented relational schema is defined only in
[createInteractionHistoryTables.sql](../../artifacts/database/createInteractionHistoryTables.sql).
It is selected with `DatabaseHistoryFormat.RelationalEvents`.
All history inserts and upserts, including V2 relational writes, are implemented in
[UpsertToDatabase.scala](../../modules/server/src/main/scala/it/evadid/server/commandHandler/sql/sync/UpsertToDatabase.scala).
`RelationalHistoryStorage` handles V2 reads and deletes; `RelationalHistorySql`
shares JDBC resource handling and row decoding without defining history writes.

A sync context is resolved from the same four strings used by `SyncContext`.
Repeated stores reuse its internal database ID. Answer upserts are identified by
context and full-precision timestamp; retries preserve IDs. Replacing a value
at the same timestamp invalidates feedback for the replaced answer within the
same transaction. Ordinary edits remain distinct, including repeated identical
answer values at different times.

Grading and scaffolding refer to an existing matching answer in that context.
When a value occurs several times, a new feedback store attaches to its latest
matching event. Reads reconstruct the current state-keyed Scala history and use
its merge rules, so feedback for A cannot replace the current answer B. Feedback
without a matching saved answer is rejected and the store transaction rolls
back; no artificial edit is created.

The single timestamp value preserves Scala nanoseconds. Timestamps are not
rounded or used as generated database IDs. Numeric database IDs never cross
the backend boundary. JSON regular feedback and entity-specific additional data
are stored separately, then reconstructed using the existing domain codecs.

V2 reads and feedback lookup share answer-row decoding. Invalid timestamps or
importance values are skipped consistently, and reads omit feedback attached to
those invalid answers while preserving valid answers in the same context.

The two classic snapshot formats remain independent supported options. This
implementation does not migrate stored records between formats or introduce
compatibility readers for obsolete serialized payloads.
 Keyless snapshot replacement
runs its delete and insert in one transaction, so a failed insert preserves the
previously saved history.

## Sync failures

`/executeCommand` accepts JSON command bodies with `Content-Encoding: gzip` or
no content encoding. The browser uses asynchronous native `CompressionStream`
for requests of at least 1,024 UTF-16 code units when available; smaller requests
and browsers without this API send plain JSON. This only changes HTTP transport:
command codecs, database formats and `SyncStrategy` filtering stay unchanged.
The proxy permits `Content-Encoding` in cross-origin requests.

Play's Netty backend decompresses gzip before the existing command parser;
there is no second application decompressor. Decoded bodies are limited to
16 MiB, including cumulative history and feedback. Larger bodies return HTTP 413
with an `ExecutionClientResponse` failure explaining the limit. Both synchronous
and asynchronous command errors use this envelope; the browser preserves its
explanation even for non-2xx responses. Transport-level gzip decoding failures are
handled by Netty before the command boundary and may surface as transport errors.
Database failures include an actionable reason, SQLState and vendor error code;
raw vendor messages remain in server logs because they can contain answer data or
credentials. Failed V2 stores roll back and do not prevent subsequent requests.

An existing sync context is reused for new timestamps. Each `(sync_id, timestamp)`
is a separate answer; retrying the same event preserves its ID. Growing histories
can cross HTTP limits independently of database keys or timestamps.

### Confirming synchronization

A destination is synced only when its fetched history contains the answers,
grading and scaffolding selected by its `SyncStrategy` (or newer merged results).
A recent fetch timestamp alone proves nothing about a store. Store failures reach
the caller and leave local history available for retry. Successful stores fetch
again even when their event timestamps predate the previous cache refresh.

### Deploying gzip support

The public proxy must allow `Content-Encoding` as well as `Authorization` and
`Content-Type`. Otherwise reads and login can succeed while the browser blocks
all compressed writes during CORS preflight. This appears as `net::ERR_FAILED`
with no HTTP response for the POST; no database write reaches the server.

Deploy the current `deployment/server/docker-compose.yml` and recreate the proxy
from its deployment directory, as well as the backend after replacing its JAR:

```sh
docker compose up -d --force-recreate proxy edusquirrel
```

Deploy the matching browser bundle separately. GitHub Pages deployment does not
update the running backend or proxy. Verify the **public** endpoint afterward
(no login or learner data is sent):

```sh
node tools/dev/check-sync-preflight.mjs \
  https://ypcgzj23.trafficplex.cloud/executeCommand https://evadid.it
```

This check rejects the older proxy header list even when OPTIONS returns 204.
The MySQL integration suite additionally sends current gzip store/fetch/delete
commands for all three supported formats and verifies feedback-only updates to
an existing answer, V2 row counts and idempotent retries.

### Browser cache schema warnings

Unreadable browser records are excluded from reads; valid records remain usable.
The warning reports a count without printing stored answers or conversations.
Old development records (for example histories with `fullHistory.events` from
before the current history schema) are unsupported and are not migrated. Remove
those obsolete records using browser developer tools under IndexedDB →
`EvaDidInteractionDB` → `variableHistoryStore`. This is separate from the server
database and from the language-triple cache. Such a warning alone does not show
that an HTTP sync request failed.
