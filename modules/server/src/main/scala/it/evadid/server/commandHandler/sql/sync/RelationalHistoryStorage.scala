package it.evadid.server.commandHandler.sql.sync

import it.evadid.core.util.crypto.ContentIdentity
import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.{EventSyncStrategy, SyncSuccess, UsageContext}
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.events.*
import java.sql.Connection
import java.time.LocalDateTime

/** Each event is independent: feedback ranges do not require the answer to fall in the same range. */
class RelationalHistoryStorage(connection: Connection, logger: Logger) extends HistorySql(connection, logger) {
  private val tables = List("answer_event", "grading_event", "scaffolding_event")

  def store(request: StoreToDbRequest): SyncSuccess = transaction {
    val context = request.syncContext
    val format = request.history.format
    val history = request.history.typedAs[format.type](format)
    val identity = List(context.programId, context.scenarioId, context.userId, context.keyForSerialisation)
    update("INSERT INTO sync_context_id (program_id,scenario_id,user_id,variable_id,format) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
      (identity :+ format.name)*)
    val (sync, savedFormat) = query("SELECT id,format FROM sync_context_id WHERE program_id=? AND scenario_id=? AND user_id=? AND variable_id=? FOR UPDATE", identity)(row => row.getLong(1) -> row.getString(2)).head
    require(InteractionFormat.fromName(savedFormat) == format, s"History format cannot change for ${context.keyForSerialisation}")
    val written = history.events.values.toList.map(event => storeEvent[format.type](sync, format)(event)).sum
    SyncSuccess(0, written, 0, LocalDateTime.now())
  }

  private def storeEvent[F <: InteractionFormat](sync: Long, format: F)(event: InteractionEvent[F]): Int = {
    val table = event match {
      case _: AnswerChangedEvent[F] => "answer_event"
      case _: GradingEvent[F] => "grading_event"
      case _: ScaffoldingEvent[F] => "scaffolding_event"
    }
    val serialized = InteractionFormat.codecsFor(format).eventSerializer.serialize(event)
    val eventId = ContentIdentity.key(ujson.read(serialized))
    val exists = query(s"SELECT id FROM $table WHERE sync_id=? AND event_id=?", List(sync, eventId))(_.getLong(1)).nonEmpty
    if exists then 0 else {
      update(s"INSERT INTO $table (sync_id,event_id,event_day,event_nano,serialized_data) VALUES (?,?,?,?,?)",
        sync, eventId, event.timestamp.toLocalDate.toEpochDay, event.timestamp.toLocalTime.toNanoOfDay, serialized)
      1
    }
  }

  private def histories(context: UsageContext, keys: Seq[String], range: Option[(LocalDateTime, LocalDateTime)]): DbFetchResponse = {
    val keyFilter = if keys.isEmpty then "" else " AND variable_id IN (" + keys.map(_ => "?").mkString(",") + ")"
    val contexts = query("SELECT id,variable_id,format FROM sync_context_id WHERE program_id=? AND scenario_id=? AND user_id=?" + keyFilter,
      List(context.programId, context.scenarioId, context.userId) ++ keys)(row => (row.getLong(1), row.getString(2), InteractionFormat.fromName(row.getString(3))))
    DbFetchResponse(contexts.map { (sync, key, format) =>
      context.toSyncContext(key) -> readHistory[format.type](sync, format, range)
    }.toMap)
  }

  private def readHistory[F <: InteractionFormat](sync: Long, format: F, range: Option[(LocalDateTime, LocalDateTime)]): InteractionHistoryJson = {
    val (filter, parameters) = range.fold("" -> List.empty[Long]) { (starting, until) =>
      val fromDay = starting.toLocalDate.toEpochDay
      val untilDay = until.toLocalDate.toEpochDay
      " AND (event_day>? OR (event_day=? AND event_nano>=?)) AND (event_day<? OR (event_day=? AND event_nano<=?))" ->
        List(fromDay, fromDay, starting.toLocalTime.toNanoOfDay, untilDay, untilDay, until.toLocalTime.toNanoOfDay)
    }
    val events = tables.flatMap { table =>
      query(s"SELECT serialized_data,event_day,event_nano FROM $table WHERE sync_id=?" + filter, List(sync) ++ parameters) { row =>
        val event = InteractionFormat.codecsFor(format).eventSerializer.deserialize(row.getString("serialized_data"))
        require(event.timestamp.toLocalDate.toEpochDay == row.getLong("event_day") &&
          event.timestamp.toLocalTime.toNanoOfDay == row.getLong("event_nano"), "Event timestamp columns disagree with the serialized event")
        require(table == (event match {
          case _: AnswerChangedEvent[F] => "answer_event"
          case _: GradingEvent[F] => "grading_event"
          case _: ScaffoldingEvent[F] => "scaffolding_event"
        }), "Event stored in the wrong table")
        event
      }
    }.toSet
    InteractionHistoryJson.fromHistory(format, InteractionVariableHistory(InteractionEvents(events)))
  }

  def fetch(request: FetchEventsFromDbRequest): DbFetchResponse = transaction {
    val range = request.strategy match {
      case EventSyncStrategy.SYNC_EVERYTHING | _: EventSyncStrategy.KeepImportance => Some(request.starting -> request.until)
      case _ => None
    }
    val response = histories(request.usageContext, request.keys, range)
    response.copy(fetchedElements = response.fetchedElements.map { (key, history) =>
      key -> history.cleanWithStrategy(request.strategy).between(request.starting, request.until)
    })
  }

  def delete(request: DeleteInDbRequest): SyncSuccess = transaction {
    val context = request.usageContext
    val scenario = if request.allWorkbooks then "" else " AND scenario_id=?"
    val limit = request.limitToKey.map(_ => " AND variable_id=?").getOrElse("")
    val parameters = List(context.programId, context.userId) ++ Option.unless(request.allWorkbooks)(context.scenarioId).toList ++ request.limitToKey.toList
    val removed = update("DELETE FROM sync_context_id WHERE program_id=? AND user_id=?" + scenario + limit, parameters*)
    SyncSuccess(0, 0, removed, LocalDateTime.now())
  }
}
