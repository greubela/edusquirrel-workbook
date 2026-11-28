package it.evadid.server.commandHandler.sql.sync

import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.sync.{EventSyncStrategy, SyncContext, SyncSuccess, UsageContext}
import it.evadid.workbook.sync.model.InteractionHistoryJson
import it.evadid.workbook.history.InteractionHistory
import it.evadid.workbook.history.events.*
import java.sql.Connection
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField
import java.util.Locale

/** Each event is independent: feedback ranges do not require the answer to fall in the same range. */
class RelationalHistoryStorage(connection: Connection, logger: Logger) extends HistorySql(connection, logger) {
  // ISO 8601 with a fixed nine-digit fraction keeps ordinary dates lexicographically sortable.
  private val timestampFormat = new DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE).appendLiteral('T').appendPattern("HH:mm:ss")
    .appendFraction(ChronoField.NANO_OF_SECOND, 9, 9, true).toFormatter(Locale.ROOT)
  private def timestampText(value: Instant): String = timestampFormat.format(value.atOffset(ZoneOffset.UTC))
  private val yearZero = Instant.parse("0000-01-01T00:00:00Z")
  private val yearTenThousand = Instant.parse("+10000-01-01T00:00:00Z")
  /** Range bounds outside years 0 to 9999, such as `Instant.MIN` and `MAX`, leave that side of the range open. */
  private def fourDigitYear(value: Instant): Boolean = !value.isBefore(yearZero) && value.isBefore(yearTenThousand)

  private val tables = List("answer_event", "grading_event", "scaffolding_event")

  /** The relational table holding events of this kind. */
  private def tableFor[F <: InteractionFormat](event: InteractionEvent[F]): String = event match {
    case _: AnswerChangedEvent[F] => "answer_event"
    case _: GradingEvent[F] => "grading_event"
    case _: ScaffoldingEvent[F] => "scaffolding_event"
  }

  def store(request: StoreToDbRequest): SyncSuccess[EventId] = transaction {
    val context = request.syncContext
    val format = request.history.format
    val history = request.history.typedAs[format.type](format)
    val identity = List(context.programId, context.scenarioId, context.userId, context.keyForSerialisation)
    statements.update("INSERT INTO sync_context_id (program_id,scenario_id,user_id,variable_id,format) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
      (identity :+ format.name)*)
    val (sync, savedFormat) = statements.query("SELECT id,format FROM sync_context_id WHERE program_id=? AND scenario_id=? AND user_id=? AND variable_id=? FOR UPDATE", identity*)(row => row.getLong(1) -> row.getString(2)).head
    // The exercise changed its type: its earlier events belong to another exercise and are replaced.
    if savedFormat != format.name then {
      tables.foreach(table => statements.update(s"DELETE FROM $table WHERE sync_id=?", sync))
      statements.update("UPDATE sync_context_id SET format=? WHERE id=?", format.name, sync)
    }
    history.events.values.toList.map(event => storeEvent[format.type](sync, format)(event))
      .foldLeft(SyncSuccess.emptyNow[EventId]())(_.combine(_))
  }

  /** Events are immutable: a stored equal event is confirmed unchanged, a different one under its ID is rejected. */
  private def storeEvent[F <: InteractionFormat](sync: Long, format: F)(event: InteractionEvent[F]): SyncSuccess[EventId] = {
    val table = tableFor(event)
    val codec = InteractionFormat.codecsFor(format).eventSerializer
    val eventId = event.eventId.value
    val existing = statements.query(s"SELECT serialized_data FROM $table WHERE sync_id=? AND event_id=?", sync, eventId)(_.getString(1)).headOption
    existing.foreach(saved => require(codec.deserialize(saved) == event, s"Event $eventId is immutable but was sent with different content"))
    if existing.isEmpty then statements.update(s"INSERT INTO $table (sync_id,event_id,timestamp_nano,serialized_data) VALUES (?,?,?,?)",
      sync, eventId, timestampText(event.timestamp), codec.serialize(event))
    SyncSuccess.immutable(Set(event.eventId), existing.map(_ => event.eventId).toSet, Set(event.eventId))
  }

  /** Stored contexts as (sync id, format name); their events are read by the per-record read rule. */
  private def contexts(context: UsageContext, keys: Seq[String]): Seq[(SyncContext, (Long, String))] = {
    val keyFilter = if keys.isEmpty then "" else " AND variable_id IN (" + keys.map(_ => "?").mkString(",") + ")"
    statements.query("SELECT id,variable_id,format FROM sync_context_id WHERE program_id=? AND scenario_id=? AND user_id=?" + keyFilter,
      (List(context.programId, context.scenarioId, context.userId) ++ keys)*)(row => context.toSyncContext(row.getString(2)) -> (row.getLong(1), row.getString(3)))
  }

  private def readHistory[F <: InteractionFormat](sync: Long, format: F, range: Option[(Instant, Instant)]): InteractionHistoryJson = {
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
    // An unreadable or inconsistent event row is discarded and reported; the other events still load.
    val events = tables.flatMap { table =>
      statements.query(s"SELECT event_id,serialized_data,timestamp_nano FROM $table WHERE sync_id=?" + filter, (sync +: parameters)*) { row =>
        val eventId = row.getString("event_id")
        scala.util.Try {
          val event = InteractionFormat.codecsFor(format).eventSerializer.deserialize(row.getString("serialized_data"))
          require(eventId == event.eventId.value, "Stored event_id must match the serialized client eventId")
          require(row.getString("timestamp_nano") == timestampText(event.timestamp),
            "timestamp_nano must match the serialized event as canonical ISO 8601 with nanosecond precision")
          require(table == tableFor(event), "Event stored in the wrong table")
          event
        }.fold(error => { logger.logExceptionWarn(s"Discarded the unreadable $table row $eventId", error); None }, Some(_))
      }.flatten
    }.toSet
    InteractionHistoryJson.fromHistory(format, InteractionHistory(format, InteractionEvents(events)))
  }

  def fetch(request: FetchEventsFromDbRequest): DbFetchResponse = transaction {
    val range = request.strategy match {
      case EventSyncStrategy.SYNC_EVERYTHING | _: EventSyncStrategy.KeepImportance => Some(request.starting -> request.until)
      case _ => None
    }
    DbFetchResponse(InteractionHistoryJson.readable(contexts(request.usageContext, request.keys))({ (sync, name) =>
        val format = InteractionFormat.fromName(name)
        readHistory[format.type](sync, format, range)
      },
      request.strategy, request.starting, request.until,
      (key, error) => logger.logExceptionWarn(s"Discarded the unreadable history of ${key.keyForSerialisation}", error)))
  }

  def delete(request: DeleteInDbRequest): SyncSuccess[SyncContext] = deleteMatching("sync_context_id", request)
}
