package it.evadid.homepage.workbook.syncDestination

import com.raquo.airstream.state.Var
import it.evadid.distribution.clients.{ExecutionClient, ExecutionClientPool}
import it.evadid.distribution.commandTypes.SQLCommands
import it.evadid.distribution.commandTypes.SQLCommands.{DbFetchResponse, DeleteInDbRequest, FetchEventsFromDbRequest}
import it.evadid.sql.DatabaseHistoryFormat
import it.evadid.workbook.sync.{SyncContext, SyncSuccess}
import it.evadid.workbook.sync.model.InteractionHistoryJson
import it.evadid.workbook.history.events.EventId
import scala.concurrent.{ExecutionContext, Future}

/** A dummy history database answering the backend's history commands like the server's
  * `RelationalHistoryStorage` and `BlobHistoryStorage`, without SQL:
  *
  *  - a stored history merges with the saved one; events are immutable, so a different event under a saved id fails;
  *  - stores acknowledge the sent events they hold afterwards, deletes the removed contexts;
  *  - a context keeps the format it was first stored with;
  *  - reads select the user's workbook and the requested keys, then apply the strategy and the inclusive range;
  *  - deletes are scoped to the user and program, and to the workbook unless all workbooks are reset.
  *
  * Through [[client]], requests and responses pass through the real command codecs, as over HTTP;
  * tests that serve their own transport call [[store]], [[fetch]] and [[delete]] directly.
  */
final class InMemoryHistoryDatabase {
  private given ExecutionContext = ExecutionContext.global

  val rows: Var[Map[(DatabaseHistoryFormat, SyncContext), InteractionHistoryJson]] = Var(Map.empty)
  val commands: Var[Vector[String]] = Var(Vector.empty)
  /** While set, every command fails, as if the database were offline. */
  val offline: Var[Boolean] = Var(false)

  private def answer[A](command: String)(run: => A): Future[A] = Future {
    commands.update(_ :+ command)
    if offline.now() then throw IllegalStateException("history database offline")
    run
  }

  def store(format: DatabaseHistoryFormat, entries: Seq[(SyncContext, InteractionHistoryJson)]): SyncSuccess[EventId] =
    // Merged outside Var.update, which would report a failure as unhandled instead of failing the request.
    entries.map { (key, incoming) =>
      val current = rows.now()
      val saved = current.get(format -> key)
      val merged = saved.fold(incoming) { saved =>
        require(saved.format == incoming.format, s"History format cannot change for ${key.keyForSerialisation}")
        saved.merge(incoming)
      }
      rows.set(current.updated(format -> key, merged))
      SyncSuccess.immutable(incoming.eventIds, saved.fold(Set.empty)(_.eventIds), merged.eventIds)
    }.foldLeft(SyncSuccess.emptyNow[EventId]())(_.combine(_))

  /** The server's read rule: unreadable records are discarded, the others filtered by strategy and range. */
  def fetch(request: FetchEventsFromDbRequest): DbFetchResponse = DbFetchResponse(InteractionHistoryJson.readable(rows.now().collect {
    case ((format, key), value) if format == request.databaseFormat && key.toUsageContext == request.usageContext &&
        (request.keys.isEmpty || request.keys.contains(key.keyForSerialisation)) => key -> value
  })(identity, request.strategy, request.starting, request.until, (_, _) => ()))

  def delete(request: DeleteInDbRequest): SyncSuccess[SyncContext] = {
    val context = request.usageContext
    def selected(format: DatabaseHistoryFormat, key: SyncContext) = format == request.databaseFormat &&
      key.programId == context.programId && key.userId == context.userId &&
      (request.allWorkbooks || key.scenarioId == context.scenarioId) && request.limitToKey.forall(_ == key.keyForSerialisation)
    val removed = rows.now().keySet.collect { case (format, key) if selected(format, key) => key }
    rows.set(rows.now().filterNot { case ((format, key), _) => selected(format, key) })
    SyncSuccess.removed(removed)
  }

  /** A saved record that cannot be read, as left by a broken or outdated client. */
  def corrupt(format: DatabaseHistoryFormat, key: SyncContext, like: InteractionHistoryJson): Unit =
    rows.set(rows.now().updated(format -> key, like.copy(historyJson = "{\"not\": \"a history\"}")))

  val client: ExecutionClient = ExecutionClientPool(List(
    SQLCommands.bulkStoreToDbCommand.toLocalExecutionClient((request, _) =>
      answer("bulkStore")(store(request.databaseFormat, request.entries.map(entry => entry.syncContext -> entry.history)))),
    SQLCommands.StoreToDbCommand.toLocalExecutionClient((request, _) =>
      answer("store")(store(request.databaseFormat, Seq(request.syncContext -> request.history)))),
    SQLCommands.fetchEventsFromDbCommand.toLocalExecutionClient((request, _) => answer("fetch")(fetch(request))),
    SQLCommands.clearValuesDbCommand.toLocalExecutionClient((request, _) => answer("clear")(delete(request))),
    SQLCommands.resetUserHistoriesDbCommand.toLocalExecutionClient((request, _) => answer("reset")(delete(request)))))
}
