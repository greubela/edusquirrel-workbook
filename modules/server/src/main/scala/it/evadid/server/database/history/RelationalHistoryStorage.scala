package it.evadid.server.database.history

import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.server.database.SqlStatements
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.interaction.sync.{EventSyncStrategy, SyncSuccess}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.events.*
import java.sql.Connection
import java.time.LocalDateTime
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField
import java.util.Locale

/** Each event is independent: feedback ranges do not require the answer to fall in the same range. */
class RelationalHistoryStorage(connection: Connection, logger: Logger) extends HistorySql(connection, logger) {
  import RelationalHistoryStorage.*

  protected val contextTable: HistorySql.ContextTableSql = sql

  def store(request: StoreToDbRequest): SyncSuccess[EventId] = transaction {
    val format = request.history.format
    val history = request.history.typedAs[format.type](format)
    val sync = lockedContextRow(request.syncContext, format, Nil)(_.getLong("id"))
    history.events.values.toList.map(event => storeEvent[format.type](sync, format)(event))
      .foldLeft(SyncSuccess.emptyNow[EventId]())(_.combine(_))
  }

  /** Events are immutable: a stored equal event is confirmed unchanged, a different one under its ID is rejected. */
  private def storeEvent[F <: InteractionFormat](sync: Long, format: F)(event: InteractionEvent[F]): SyncSuccess[EventId] = {
    val table = EventTable.of(event)
    val codec = InteractionFormat.codecsFor(format).eventSerializer
    val eventId = event.eventId.value
    val existing = statements.query(table.selectEvent, sync, eventId)(_.getString(1)).headOption
    existing.foreach(saved => require(codec.deserialize(saved) == event, s"Event $eventId is immutable but was sent with different content"))
    if existing.isEmpty then statements.update(table.insertEvent, sync, eventId, timestampText(event.timestamp), codec.serialize(event))
    SyncSuccess.immutable(Set(event.eventId), existing.map(_ => event.eventId).toSet, Set(event.eventId))
  }

  private def readHistory[F <: InteractionFormat](sync: Long, format: F, range: Option[(LocalDateTime, LocalDateTime)]): InteractionHistoryJson = {
    // ISO expanded positive years begin with '+', which sorts before four-digit years. Bounds outside 0000..9999 are
    // left to the precise typed filtering of [[readable]] rather than to an incorrect text order.
    val bounds = SqlStatements.optionalFilter(range.map(_._1).filter(fourDigitYear).map(timestampText)) ++
      SqlStatements.optionalFilter(range.map(_._2).filter(fourDigitYear).map(timestampText))
    // An unreadable or inconsistent event row is discarded and reported; the other events still load.
    val events = EventTable.values.toSeq.flatMap { table =>
      statements.query(table.selectRange, (sync +: bounds)*) { row =>
        val eventId = row.getString("event_id")
        scala.util.Try {
          val event = InteractionFormat.codecsFor(format).eventSerializer.deserialize(row.getString("serialized_data"))
          require(eventId == event.eventId.value, "Stored event_id must match the serialized client eventId")
          require(row.getString("timestamp_nano") == timestampText(event.timestamp),
            "timestamp_nano must match the serialized event as canonical ISO 8601 with nanosecond precision")
          require(table == EventTable.of(event), "Event stored in the wrong table")
          event
        }.fold(error => { logger.logExceptionWarn(s"Discarded the unreadable ${table.name} row $eventId", error); None }, Some(_))
      }.flatten
    }.toSet
    InteractionHistoryJson.fromHistory(format, InteractionVariableHistory(InteractionEvents(events)))
  }

  def fetch(request: FetchEventsFromDbRequest): DbFetchResponse = transaction {
    val range = request.strategy match {
      case EventSyncStrategy.SYNC_EVERYTHING | _: EventSyncStrategy.KeepImportance => Some(request.starting -> request.until)
      case _ => None
    }
    readable(request, contextRows(request.usageContext, request.keys)(row => row.getLong("id") -> row.getString("format"))) {
      (sync, name) =>
        val format = InteractionFormat.fromName(name)
        readHistory[format.type](sync, format, range)
    }
  }
}

object RelationalHistoryStorage {
  // ISO 8601 with a fixed nine-digit fraction keeps ordinary dates lexicographically sortable.
  private val timestampFormat = new DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE).appendLiteral('T').appendPattern("HH:mm:ss")
    .appendFraction(ChronoField.NANO_OF_SECOND, 9, 9, true).toFormatter(Locale.ROOT)
  private def timestampText(value: LocalDateTime): String = timestampFormat.format(value)
  private def fourDigitYear(value: LocalDateTime): Boolean = value.getYear >= 0 && value.getYear <= 9999

  private val sql = HistorySql.ContextTableSql(
    insertIfAbsent = "INSERT INTO sync_context_id (program_id,scenario_id,user_id,variable_id,format) " +
      "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
    lockRow = "SELECT id,format FROM sync_context_id WHERE program_id=? AND scenario_id=? AND user_id=? AND variable_id=? FOR UPDATE",
    selectAll = "SELECT id,variable_id,format FROM sync_context_id WHERE program_id=? AND scenario_id=? AND user_id=?",
    selectKeys = "SELECT id,variable_id,format FROM sync_context_id " +
      "WHERE program_id=? AND scenario_id=? AND user_id=? AND JSON_CONTAINS(?, JSON_QUOTE(variable_id))",
    lockDeleted = "SELECT scenario_id,variable_id FROM sync_context_id " +
      "WHERE program_id=? AND user_id=? AND (?=1 OR scenario_id=?) AND (?=0 OR variable_id=?) FOR UPDATE",
    // Events follow their context through ON DELETE CASCADE.
    delete = "DELETE FROM sync_context_id WHERE program_id=? AND user_id=? AND (?=1 OR scenario_id=?) AND (?=0 OR variable_id=?)")

  /** The table of each event kind with its constant statements. `selectRange` takes the sync ID and two optional
    * bounds, the lower one also admitting expanded `+` years and the upper one excluding them.
    */
  private enum EventTable(val name: String, val selectEvent: String, val insertEvent: String, val selectRange: String) {
    case Answer extends EventTable("answer_event",
      "SELECT serialized_data FROM answer_event WHERE sync_id=? AND event_id=?",
      "INSERT INTO answer_event (sync_id,event_id,timestamp_nano,serialized_data) VALUES (?,?,?,?)",
      "SELECT event_id,serialized_data,timestamp_nano FROM answer_event WHERE sync_id=? " +
        "AND (?=0 OR timestamp_nano>=? OR timestamp_nano LIKE '+%') AND (?=0 OR (timestamp_nano<=? AND timestamp_nano NOT LIKE '+%'))")
    case Grading extends EventTable("grading_event",
      "SELECT serialized_data FROM grading_event WHERE sync_id=? AND event_id=?",
      "INSERT INTO grading_event (sync_id,event_id,timestamp_nano,serialized_data) VALUES (?,?,?,?)",
      "SELECT event_id,serialized_data,timestamp_nano FROM grading_event WHERE sync_id=? " +
        "AND (?=0 OR timestamp_nano>=? OR timestamp_nano LIKE '+%') AND (?=0 OR (timestamp_nano<=? AND timestamp_nano NOT LIKE '+%'))")
    case Scaffolding extends EventTable("scaffolding_event",
      "SELECT serialized_data FROM scaffolding_event WHERE sync_id=? AND event_id=?",
      "INSERT INTO scaffolding_event (sync_id,event_id,timestamp_nano,serialized_data) VALUES (?,?,?,?)",
      "SELECT event_id,serialized_data,timestamp_nano FROM scaffolding_event WHERE sync_id=? " +
        "AND (?=0 OR timestamp_nano>=? OR timestamp_nano LIKE '+%') AND (?=0 OR (timestamp_nano<=? AND timestamp_nano NOT LIKE '+%'))")
  }

  private object EventTable {
    def of[F <: InteractionFormat](event: InteractionEvent[F]): EventTable = event match {
      case _: AnswerChangedEvent[F] => Answer
      case _: GradingEvent[F] => Grading
      case _: ScaffoldingEvent[F] => Scaffolding
    }
  }
}
