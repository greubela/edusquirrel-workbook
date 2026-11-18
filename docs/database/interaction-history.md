# Interaction history storage

Both `DatabaseHistoryFormat.BlobFormat` and `RelationalFormat` store the current
format-based event history. Their authoritative schema is
[setup.sql](../../artifacts/database/setup.sql).
The single history-read command is `fetch-events-from-db-request`. Every read
provides inclusive bounds and an event strategy; complete reads use
`LocalDateTime.MIN` and `MAX`. Selection is applied before the timestamp window.

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
also own reads and scoped deletes. `HistorySql` accepts only typed string/long SQL parameters and owns resource handling and
transactions. Failed requests roll back. Unknown formats, invalid event payloads
and inconsistent stored metadata fail explicitly. Only current schemas are supported;
there are no classic formats, migrations or compatibility aliases.

## Synchronization and transport

`RemoteHistoryCache` tracks local events, confirmed remote events and read progress
separately. Writes send only the destination strategy's missing events. A successful
write acknowledges exactly that delta and does not advance the read cursor. Failed
writes remain pending. Read cursors replay the last event timestamp inclusively;
normal polls replay the last event inclusively. A full refresh every five minutes
recovers late uploads with older event timestamps.

`RemoteInteractionCacheControl` selects a replaceable workbook binding from the
user/workbook/credential environment signal. Each typed binding owns a `Var` containing
an immutable `RemoteHistoryCache.CacheState`, including the map of destination to
confirmed events and read progress. The shared cache applies immutable updates through
that state handle. Bindings have no active/closing lifecycle flags: switching signal
subscriptions disconnects old restorations, while captured histories and handlers let
departing requests finish with their original credentials as the replacement syncs.
Normal user changes start the replacement immediately; a running full reset finishes
first because it also clears shared local answer stores. `FormattedSyncDestination` encodes/decodes envelopes at the
destination boundary. Local writes merge deltas with the saved typed history;
IndexedDB performs the read/merge/write in a single read-write transaction.
Each successful destination read is applied independently, so an unavailable
backend cannot block local restoration. Outdated responses cannot change a newly
selected workbook or user. Buffered batches register before delegates are flushed; merges or delegate
failures complete every waiting promise. A sync phase registers all interactions
before flushing shared handlers. Read failures are reported after pending writes
have also been attempted; one unavailable destination cannot suppress healthy writes. The sync indicator reflects confirmed
the latest answer and all its associated grading/scaffolding events, including running feedback.
Older unsent history does not prevent confirmation.

Defaults send all events to both backend layouts and `SYNC_MAJOR` events to local
IndexedDB. Strategies select transmissions; stores union events and never prune
previously stored data. Confirmed events are excluded from subsequent write deltas.

`sync-histories-to-db-request` carries `syncContexts: Seq[SyncContext]` and
`histories: Seq[InteractionHistoryJson]` in matching order. A buffered flush merges
queued deltas per key into one network request. The backend authorizes every context
before processing and commits the whole batch in one transaction; malformed entries
roll back all changes. Range reads with the same context, bounds and strategy share
one request over their combined bounds, clipped back to each caller’s exact window.

`DatabaseHistoryFormat.tables` exposes `DatabaseTable` declarations with typed
`DatabaseColumn`/`MySqlType` metadata; tests check these against the setup SQL.

Full reset drains registered operations, deletes the current user’s histories across
all workbooks from every backend destination, and clears each configured local
answer store without decoding records. Synchronization remains paused after partial
failure until retry; navigation and credential replacement preserve the user's reset
state. A different user receives a separate binding, and late reset completions update
only their originating user. Successful reset reloads the workbook.
Reset also works from the catalogue without a loaded workbook. Navigation flushes
the previous workbook’s queued writes before releasing its handlers. Authentication
and unrelated IndexedDB caches are preserved. User changes and full
resets invalidate pending feedback and computation caches; stale completions cannot
publish into a replacement session.

`/executeCommand` accepts JSON and gzip bodies. Native browser compression is used
for larger requests; decoded bodies are limited to 16 MiB. The proxy must allow
`Content-Encoding`, `Authorization` and `Content-Type`. Failed HTTP commands retain
their error envelope; database errors expose a useful reason, SQLState and vendor
code without placing credentials or full answer contents in the client response.

Binding-switch regressions in
[`RemoteInteractionCacheControlSpec`](../../modules/client/src/test/scala/it/evadid/homepage/control/change/RemoteInteractionCacheControlSpec.scala)
exercise the production controller, shared buffers and HTTP adapter with gated responses.
They cover colliding IDs across workbooks, A → B → A with successful/failed retired reads,
destination changes, credential rotation, delayed and
failed acknowledgements, reset ownership, and selection inside an Airstream observer.
Language/display changes preserve the existing cursor and confirmations. Run them with
`sbt 'client/testOnly *RemoteInteractionCacheControlSpec'`.

See [database setup](README.md) for schema inspection and credentials. The MySQL
integration suite covers both layouts, concurrent blob writes, same-time events,
full-precision ranges, feedback-only deltas, transaction rollback, gzip HTTP commands
and authenticated reset scopes. Run it only against the disposable localhost
`edusquirrel_account_test` database:

```sh
sbt 'server / MysqlIntegration / test'
```
