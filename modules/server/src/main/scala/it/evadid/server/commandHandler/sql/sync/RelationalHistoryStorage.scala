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
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField
import java.util.Locale

/** Each event is independent: feedback ranges do not require the answer to fall in the same range. */
class RelationalHistoryStorage(connection: Connection, logger: Logger) extends HistorySql(connection, logger) {
  // ISO 8601 with a fixed nine-digit fraction keeps ordinary dates lexicographically sortable.
  private val timestampFormat = new DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE).appendLiteral('T').appendPattern("HH:mm:ss")
    .appendFraction(ChronoField.NANO_OF_SECOND, 9, 9, true).toFormatter(Locale.ROOT)
  private def timestampText(value: LocalDateTime): String = timestampFormat.format(value)
  private def fourDigitYear(value: LocalDateTime): Boolean = value.getYear >= 0 && value.getYear <= 9999

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
      update(s"INSERT INTO $table (sync_id,event_id,timestamp_nano,serialized_data) VALUES (?,?,?,?)",
        sync, eventId, timestampText(event.timestamp), serialized)
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
    // ISO expanded positive years begin with '+', which sorts before four-digit years.
    // Include them in lower-bound candidates; precise typed filtering below handles all years.
    // Bounds outside 0000..9999 are left to that filter rather than using an incorrect text order.
    val bounds = range.toList.flatMap { (starting, until) =>
      Seq(
        Option.when(fourDigitYear(starting))("(timestamp_nano>=? OR timestamp_nano LIKE '+%')" -> timestampText(starting)),
        Option.when(fourDigitYear(until))("(timestamp_nano<=? AND timestamp_nano NOT LIKE '+%')" -> timestampText(until))
      ).flatten
    }
    val filter = bounds.map((sql, _) => " AND " + sql).mkString
    val parameters = bounds.map(_._2)
    val events = tables.flatMap { table =>
      query(s"SELECT serialized_data,timestamp_nano FROM $table WHERE sync_id=?" + filter, List(sync) ++ parameters) { row =>
        val event = InteractionFormat.codecsFor(format).eventSerializer.deserialize(row.getString("serialized_data"))
        require(row.getString("timestamp_nano") == timestampText(event.timestamp),
          "timestamp_nano must match the serialized event as canonical ISO 8601 with nanosecond precision")
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
