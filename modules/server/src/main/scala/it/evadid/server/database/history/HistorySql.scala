package it.evadid.server.database.history

import it.evadid.distribution.commandTypes.SQLCommands.{BulkStoreToDbRequest, DbFetchResponse, DeleteInDbRequest, FetchEventsFromDbRequest, StoreToDbRequest}
import it.evadid.server.database.{Database, SqlStatements}
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.sync.model.InteractionHistoryJson
import it.evadid.workbook.sync.{SyncContext, SyncSuccess, UsageContext}
import it.evadid.workbook.history.events.EventId
import java.sql.{Connection, ResultSet}

/** Shared statements, row identity and transaction ownership for both history layouts. All SQL is constant text. */
abstract class HistorySql(connection: Connection, logger: Logger) {
  import HistorySql.*

  protected final val statements = SqlStatements(connection, logger)

  /** Constant SQL of the table holding one row per sync context. */
  protected def contextTable: ContextTableSql

  def store(request: StoreToDbRequest): SyncSuccess[EventId]
  def fetch(request: FetchEventsFromDbRequest): DbFetchResponse

  /** One transaction and deterministic lock order across the entire network batch. */
  final def storeAll(request: BulkStoreToDbRequest): SyncSuccess[EventId] = transaction {
    request.entries.sortBy { entry =>
      val context = entry.syncContext
      (context.programId, context.scenarioId, context.userId, context.keyForSerialisation)
    }
      .foldLeft(SyncSuccess.emptyNow[EventId]())((result, entry) => result.combine(store(entry)))
  }

  /** Deletes the request's context rows (with their events), reporting the removed contexts. */
  final def delete(request: DeleteInDbRequest): SyncSuccess[SyncContext] = transaction {
    val context = request.usageContext
    // Bound as `(? = 1 OR scenario_id = ?)` and `(? = 0 OR variable_id = ?)`.
    val parameters = Seq(context.programId, context.userId, if request.allWorkbooks then 1L else 0L, context.scenarioId) ++
      SqlStatements.optionalFilter(request.limitToKey)
    val removed = statements.query(contextTable.lockDeleted, parameters*)(row =>
      SyncContext(context.programId, row.getString(1), context.userId, row.getString(2))).toSet
    statements.update(contextTable.delete, parameters*)
    SyncSuccess.removed(removed)
  }

  /** Inserts the context's row unless present, then locks it until the transaction ends and reads it. `read` also
    * receives whether the stored format is `format`: an exercise whose type changed replaces its earlier history.
    */
  protected final def lockedContextRow[A](context: SyncContext, format: InteractionFormat, initial: Seq[String])
                                         (read: (ResultSet, Boolean) => A): A = {
    val key = identity(context)
    statements.update(contextTable.insertIfAbsent, (key ++ (format.name +: initial))*)
    statements.query(contextTable.lockRow, key*)(row => read(row, row.getString("format") == format.name)).head
  }

  /** The usage context's rows, optionally limited to `keys`, as (sync context, stored value). */
  protected final def contextRows[A](context: UsageContext, keys: Seq[String])(read: ResultSet => A): Seq[(SyncContext, A)] = {
    val values = Seq(context.programId, context.scenarioId, context.userId)
    val rows = if keys.isEmpty then statements.query(contextTable.selectAll, values*)(row => row.getString("variable_id") -> read(row))
    else statements.query(contextTable.selectKeys, (values :+ jsonArray(keys))*)(row => row.getString("variable_id") -> read(row))
    rows.map((key, value) => context.toSyncContext(key) -> value)
  }

  /** Decodes stored records; an unreadable one is discarded and reported, the others still load. */
  protected final def readable[S](request: FetchEventsFromDbRequest, stored: Seq[(SyncContext, S)])(decode: S => InteractionHistoryJson): DbFetchResponse =
    DbFetchResponse(InteractionHistoryJson.readable(stored)(decode, request.strategy, request.starting, request.until,
      (key, error) => logger.logExceptionWarn(s"Discarded the unreadable history of ${key.keyForSerialisation}", error)))

  protected final def transaction[A](run: => A): A = Database.transaction(connection)(run)
}

object HistorySql {
  /** Constant statements of a context table. Parameters: the four-part [[identity]]; `insertIfAbsent` adds the
    * format and the storage's initial columns; `selectAll`/`selectKeys` take program, scenario and user (and a JSON
    * array of variable IDs); `lockDeleted`/`delete` take program, user and the two optional filters of [[HistorySql.delete]].
    */
  final case class ContextTableSql(insertIfAbsent: String, lockRow: String, selectAll: String, selectKeys: String,
                                   lockDeleted: String, delete: String)

  /** Program, scenario, user and variable: the unique identity of a context row. */
  def identity(context: SyncContext): Seq[String] =
    Seq(context.programId, context.scenarioId, context.userId, context.keyForSerialisation)

  /** Bound to `JSON_CONTAINS(?, JSON_QUOTE(variable_id))`, which matches with the column's binary collation. */
  private def jsonArray(keys: Seq[String]): String = ujson.write(ujson.Arr.from(keys.map(ujson.Str(_))))
}
