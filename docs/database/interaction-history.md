# History storage and synchronization

Histories use typed events and the self-describing
`InteractionHistoryJson(format, historyJson)` envelope. Servers decode each entry
through its `InteractionFormat`, independently of workbook definitions or element
registries. See [formats and event semantics](../interaction-format.md).

## Classes and ownership

| Class | Responsibility |
| --- | --- |
| [HomepageState](../../modules/client/src/main/scala/it/evadid/homepage/control/change/HomepageState.scala) | Orders user, credential and workbook changes across the page and answer controls |
| [InteractionController](../../modules/core/shared/src/main/scala/it/evadid/workbook/control/InteractionController.scala) | Transitions of the answers (`InteractionInfo`), deferred stores, batched store/read phases, user-scoped reset progress, session-scoped results |
| [RemoteHistoryCache](../../modules/core/shared/src/main/scala/it/evadid/core/datastructures/storage/remote/RemoteHistoryCache.scala) | Store and read logic over an immutable `CacheState`: local histories and per-location known event IDs, read freshness and cursors |
| [FormattedSyncDestination](../../modules/core/shared/src/main/scala/it/evadid/workbook/sync/FormattedSyncDestination.scala) | Converts typed histories to/from envelopes at a destination boundary |
| [BufferedSyncDestinationHandler](../../modules/core/shared/src/main/scala/it/evadid/workbook/sync/destination/BufferedSyncDestinationHandler.scala) | Merges queued deltas per context, serializes batches and gives each caller the confirmations of its own events |
| [DatabaseSyncViaBackendServerHandler](../../modules/client/src/main/scala/it/evadid/homepage/workbook/syncDestination/DatabaseSyncViaBackendServerHandler.scala) | Sends database commands using captured credentials and connection configuration |
| [BackendCommandHandler](../../modules/server/src/main/scala/it/evadid/server/BackendCommandHandler.scala) | Decodes typed requests and authorizes every context |
| [Server history handlers](../../modules/server/src/main/scala/it/evadid/server/commandHandler/sql/sync/) | `HistorySql.forRequest` dispatches to the selected layout; `HistorySql` owns transactions; [`SqlStatements`](../../modules/server/src/main/scala/it/evadid/server/database/SqlStatements.scala) binds typed parameters and closes statements |

Each interaction's entry in `InteractionInfo` holds its `CacheState` (history and
destination knowledge); see [answer state](../state-and-serialization.md#answer-state-and-sessions).
Reset progress and pending operations are kept per user across workbooks and credentials.

## Binding and event flow

1. A change of a history is observed and its store requested after the current task.
2. The cache computes missing events independently for each destination.
3. A phase registers all requests before flushing shared handlers. Buffered futures
   complete with the actual read/write result; awaiting queued work requires a flush.
4. Results apply only to the session they started in, and only for destinations its
   connection still uses.

A context change (user, workbook, full reset) sends pending stores, then replaces the
session; a credential or destination change reconnects the same session. Both start a
full read. This isolates credentials and responses while allowing old and new requests to
overlap: a retired session's requests may finish or fail, but cannot change its successor.

Backend databases store, read and delete histories only for a verified account. These
commands are `requiresLogin` factories: the browser's remote client, which attaches the
signed token, fails them locally without a token or with an expired one.

`EventSyncStrategy` selects transmissions, not retention, and never prunes persisted
history. `SYNC_EVERYTHING` sends all missing events, TEMPORARY ones included; `SYNC_MAJOR`
selects major answers plus the latest answer that is not TEMPORARY; `SYNC_LAST` selects only
the latest answer. Both include all feedback of the selected answers, pending lifecycle events
included. Other importance strategies filter individual events; latest/count strategies retain
timestamp ties, and `Since` includes its boundary. Defaults send `SYNC_MAJOR` to both backend
layouts and `SYNC_LAST` to local IndexedDB.
Stores union events and apply `cleanedHistory()`, so superseded DEFAULT and
TEMPORARY answers do not accumulate. IndexedDB merges within one read-write
transaction. Malformed local records are excluded individually; malformed backend
requests or persisted database events fail explicitly.

Each destination is tracked by the IDs of the events it is known to hold: those a
read returned or a committed write acknowledged.
[`SyncSuccess[T]`](../../modules/core/shared/src/main/scala/it/evadid/workbook/sync/SyncSuccess.scala)
names the added, changed, unchanged and removed elements (`EventId`s for history
stores, `SyncContext`s for removals, keys for raw key-value stores); its
`elementsStored` were present at commit. A requested write alone is not evidence.
A store sends the strategy's selection minus the known events; the buffered handler
merges repeated requests until it flushes and gives each caller the confirmations of
its own events. Failed or unacknowledged writes are resent. Acknowledgments do not
advance read cursors or freshness; a read event whose ID names different local
content is discarded. An explicit sync from the sync icon commits the current answer
as MAJOR, stores, flushes and reads. History updates schedule
their store after the current task; a replaced binding runs its pending stores
before its final flush. Normal reads replay the last received timestamp inclusively. A poll performs a
full refresh once the last full read is five minutes old, also recovering late
uploads with older timestamps. Incremental polls are cheaper, but cannot discover
those events immediately. Explicit window
reads do not mark unread portions fresh. Independent read failures do not suppress
healthy writes. The sync icon requires the latest answer and all its associated
feedback events, including pending feedback; older unsent events do not block it.
Under `SYNC_MAJOR` a TEMPORARY latest answer therefore shows as unsynchronized
until it is committed.

## Storage layouts and trade-offs

| Layout | Representation | Trade-off |
| --- | --- | --- |
| `BlobHistoryStorage` / `BlobFormat` | One format name and complete history JSON in `blob_history` per context | Simple storage; merges lock and decode the whole history, and ranges filter decoded events |
| `RelationalHistoryStorage` / `RelationalFormat` | Format metadata on `sync_context_id`; independent answer, grading and scaffolding event rows | More tables and writes; indexed ranges can fetch feedback without fetching its answer event |

Identity is `(programId, scenarioId, userId, keyForSerialisation)`. The format is
metadata, not part of the unique key. A store under another format (the exercise
changed its type) replaces the record, even if the two formats share a JSON schema;
the earlier answers belonged to a different exercise. Exercise IDs are the storage
keys: a new ID is a new exercise. Case and trailing spaces are significant; numeric
row IDs stay on the server. Each event receives a client-generated `EventId` once;
serialization, copies and retries preserve it, and the event is immutable. Both
layouts confirm a replayed equal event as unchanged and reject a request that sends
different content under a stored ID; relational rows are unique per `(sync_id, event_id)`.
Timestamps are never adjusted to manufacture uniqueness: distinct same-time events
survive, while replaying an event cannot create another row. Saved events without
an ID are unsupported. History storage grows as new events are recorded; transmission strategies do not provide deletion or retention policies.

### Timestamp representation

`timestamp_nano` stores the event's instant as ISO 8601 UTC date/time text (no zone suffix) with exactly nine fractional
digits, e.g. `2026-11-18T12:00:00.123456789`. MySQL `DATETIME` supports only six.
The binary text index serves range queries, so a duplicate `timestamp_millies`
column is unnecessary. No epoch-day or nanosecond-of-day encoding is stored.
Four-digit years sort chronologically; signed/expanded ISO years use safe SQL
candidate bounds followed by typed filtering. Events range over UTC date-times from
`LocalDateTime.MIN` to `MAX`; `Instant.MIN` and `MAX` are open read bounds. Reads verify the canonical text against the serialized event.

Both layouts decode through the declared format; JDBC bindings accept only
`String | Long`. `DatabaseHistoryFormat.tables` exposes typed `DatabaseTable`,
`DatabaseColumn` and `MySqlType` declarations checked against
[setup.sql](../../deployment/database/setup.sql).
Existing event schemas require an explicit update to `timestamp_nano`; setup
initializes empty databases only and never alters existing tables.

## Bulk writes and range reads

Production writes use `bulkStoreToDbCommand` (`sync-histories-to-db-request`).
A nonempty buffered flush makes one request per destination. Parallel
`syncContexts` and `histories` sequences retain entry identity; events from different
interactions never merge. Two contexts sent to blob and relational storage mean
four context/destination updates in two network requests. The SQL statement count
can be larger because relational storage writes individual events.

For direct server integrations, supply an authenticated backend client and typed
envelopes (the two histories may use different formats):

```scala
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.commandTypes.SQLCommands
import it.evadid.sql.BackendDatabase
import it.evadid.sql.DatabaseHistoryFormat.BlobFormat
import it.evadid.workbook.sync.{SyncSuccess, UsageContext}
import it.evadid.workbook.sync.model.InteractionHistoryJson
import it.evadid.workbook.history.events.EventId
import scala.concurrent.{ExecutionContext, Future}

def storePair(backend: ExecutionClient, context: UsageContext,
    first: InteractionHistoryJson, second: InteractionHistoryJson)
    (using ExecutionContext): Future[SyncSuccess[EventId]] = {
  val request = SQLCommands.BulkStoreToDbRequest(
    syncContexts = Seq(context.toSyncContext("first_history"), context.toSyncContext("second_history")),
    histories = Seq(first, second), databaseConfig = BackendDatabase, databaseFormat = BlobFormat)
  SQLCommands.bulkStoreToDbCommand.sendCommandTo(backend, request).map(_.result)
}
```

The backend authorizes every context before storing anything. A batch commits
atomically; one invalid entry rolls it all back. Retrying exact events is safe and
confirms them as `elementsUnchanged`. Deletes report the removed contexts.

The sole read command is `fetch-events-from-db-request`, with inclusive bounds;
complete reads use `Instant.MIN` and `MAX`. Selection precedes the timestamp
window. Buffered reads sharing a usage context and strategy query the union of
keys/windows once, then clip each caller's result to its own bounds.

`/executeCommand` accepts JSON or gzip, with a decoded-body limit of 16 MiB.
The proxy must permit `Content-Encoding`, `Authorization` and `Content-Type`.
Errors preserve their failure envelope; SQL diagnostics omit bound values and
credentials from client-visible cause chains.

## Full reset

`resetAllData()` drains registered operations, deletes the current user's histories
across all workbooks from every configured backend destination, and clears each
configured local answer store without decoding records. Authentication and
unrelated caches survive. Reset also works from the catalogue.

Partial failure keeps sync paused until retry, including across navigation and
credential changes. A different user has separate reset state; late completions
cannot change it. Normal user switches start the replacement immediately, while
a running reset finishes first because it clears shared local stores. Successful
reset reloads the page. User changes and full resets start a new session, whose feedback
caches are new, so stale completions cannot publish into it.

## Verification

`HomepageStateSpec` covers production buffers and HTTP adaptation
with controlled responses: workbook/user/credential/destination switches,
A → B → A, delayed successes/failures, confirmation isolation and reset ownership.
`InteractionGradingConcurrencySpec` covers grading while answers change and other devices
record feedback; `RemoteHistoryCacheSpec` and `BufferedSyncDestinationHandlerSpec` cover cache
and batch contracts. MySQL tests cover both layouts, mixed-format HTTP batches,
format replacement, exact ISO timestamp storage, signed/expanded years, nanosecond
range boundaries, corruption rejection, rollback and authorization.

```sh
sbt 'client/testOnly *HomepageStateSpec'
sbt 'coreJVM/testOnly *InteractionControllerSpec *InteractionGradingConcurrencySpec *RemoteHistoryCacheSpec *BufferedSyncDestinationHandlerSpec'
# Only against the disposable localhost edusquirrel_account_test database:
sbt 'server / MysqlIntegration / test'
```

See [setup and connection configuration](README.md) and the [test guide](../core-package-unit-tests.md).
