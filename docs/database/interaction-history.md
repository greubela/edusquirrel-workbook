# Interaction history storage

Both `DatabaseHistoryFormat.BlobFormat` and `RelationalFormat` store the current
format-based event history. Their authoritative schema is
[setup.sql](../../artifacts/database/setup.sql).
Requests carry `InteractionHistoryJson(format, historyJson)`; the backend never
needs a workbook definition or element-ID registry to decode them.

## Persistence layouts

| Format | Representation | Merge and range behavior |
| --- | --- | --- |
| `BlobFormat` | One format name and complete history JSON in `blob_history` per sync context | A transaction locks the row, decodes and unions typed histories, then writes the result. Range reads filter typed events. |
| `RelationalFormat` | Format name on `sync_context_id`; one row per answer, grading or scaffolding event in its dedicated table | Content hashes deduplicate exact events. Indexed range queries read each event table independently. |

Context identity is `(programId, scenarioId, userId, keyForSerialisation)`. Its
format is metadata, not part of the primary/unique key; changing the format for an
existing context is rejected. Case and trailing spaces remain significant.
Database-generated IDs stay on the server.

Every relational event row contains a typed event JSON payload, exact ISO timestamp,
epoch day and nanosecond-of-day. The numeric pair supports inclusive SQL ranges
across the full `LocalDateTime` domain, including adjacent nanoseconds. Decoding
checks that all timestamp columns agree. Feedback carries its associated state and
can be fetched or stored without an answer event in the same range. No artificial
answer edits or foreign-key links to answer versions are required.

Distinct events at the same timestamp are retained. A replay preserves the existing
row; blob merges likewise retain all distinct events. Neither layout replaces a
history snapshot or collapses feedback lifecycle events during persistence.

[`UpsertToDatabase`](../../modules/server/src/main/scala/it/evadid/server/commandHandler/sql/sync/UpsertToDatabase.scala)
dispatches writes to `BlobHistoryStorage` or `RelationalHistoryStorage`; those classes
also own reads and scoped deletes. `HistorySql` owns JDBC resource handling and
transactions. Failed requests roll back. Unknown formats, invalid event payloads
and inconsistent stored metadata fail explicitly. Only current schemas are supported;
there are no classic formats, migrations or compatibility aliases.

## Synchronization and transport

`RemoteHistoryCache` tracks local events, confirmed remote events and read progress
separately. Writes send only the destination strategy's missing events. A successful
write acknowledges exactly that delta and does not advance the read cursor. Failed
writes remain pending. Read cursors replay the last event timestamp inclusively;
full reads recover late uploads with older event timestamps.

`RemoteInteractionCacheControl` owns a typed cache for each interaction in the active
workbook/user environment. `FormattedSyncDestination` encodes/decodes envelopes at the
destination boundary. Outdated responses cannot change a newly selected workbook or
user. Buffered batches register before delegates are flushed; merges or delegate
failures complete every waiting promise. The sync indicator reflects confirmed
strategy-selected events, rather than request/fetch times.

`/executeCommand` accepts JSON and gzip bodies. Native browser compression is used
for larger requests; decoded bodies are limited to 16 MiB. The proxy must allow
`Content-Encoding`, `Authorization` and `Content-Type`. Failed HTTP commands retain
their error envelope; database errors expose a useful reason, SQLState and vendor
code without placing credentials or full answer contents in the client response.

See [database setup](README.md) for schema inspection and credentials. The MySQL
integration suite covers both layouts, concurrent blob writes, same-time events,
full-precision ranges, feedback-only deltas, transaction rollback, gzip HTTP commands
and authenticated reset scopes. Run it only against the disposable localhost
`edusquirrel_account_test` database:

```sh
sbt 'server / MysqlIntegration / test'
```
