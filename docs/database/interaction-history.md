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

The two classic snapshot formats remain independent supported options. This
implementation does not migrate stored records between formats or introduce
compatibility readers for obsolete serialized payloads.

## Sync failures

`/executeCommand` accepts UTF-8 command bodies up to 16 MiB, including cumulative
history and feedback. Larger bodies return HTTP 413 with an `ExecutionClientResponse`
failure explaining the limit. Both synchronous and asynchronous command errors use
this envelope; the browser preserves its explanation even for non-2xx responses.
Database failures include an actionable reason, SQLState and vendor error code;
raw vendor messages remain in server logs because they can contain answer data or
credentials. Failed V2 stores roll back and do not prevent subsequent requests.

An existing sync context is reused for new timestamps. Each `(sync_id, timestamp)`
is a separate answer; retrying the same event preserves its ID. Growing histories
can cross HTTP limits independently of database keys or timestamps.
