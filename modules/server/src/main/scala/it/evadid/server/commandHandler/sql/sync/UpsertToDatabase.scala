package it.evadid.server.commandHandler.sql.sync

import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.ExecutionCommandFactory.ExecutionCommand
import it.evadid.distribution.command.{ExecutionCommandFactory, SerializedException}
import it.evadid.distribution.commandTypes.SQLCommands.{DeleteInDbRequest, FetchAllFromDbRequest, FetchEventsFromDbRequest, StoreToDbRequest}
import it.evadid.sql.DatabaseConfiguration
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import upickle.default.*

import java.time.LocalDateTime
import java.util
import java.util.zip.Deflater
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.util.io.ParsingContext
import it.evadid.distribution.command.ExecutionInfo.ExecutionSuccess
import it.evadid.server.AuthHandling
import it.evadid.server.util.Using
import it.evadid.sql.DatabaseHistoryFormat
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.SyncLogger
import upickle.default.{ReadWriter, read, write}

import java.sql.Connection
import scala.jdk.CollectionConverters.*
import scala.util.Using

object UpsertToDatabase {
  private val logger = Logger("sql.upsert")

  def handleRequest(request: StoreToDbRequest, logger: Logger): SyncSuccess = {
    val databaseConfig = request.databaseConfig
    val connection = databaseConfig.connection
    val databaseFormat = request.databaseFormat
    val control = databaseFormat match {
      case DatabaseHistoryFormat.RelationalEvents => new UpsertToRelationalTables(connection, request.usageContext, logger)
      case DatabaseHistoryFormat.SingleBlob => new UpsertToSingleBlobDatabase(connection, request.usageContext, logger)
    }
    control.upsert(request)
  }
}

object UpsertToRelationalTables {
  def handleRequest(connection: Connection, request: StoreToDbRequest, logger: Logger): SyncSuccess = {
    val control = new UpsertToRelationalTables(connection, request.usageContext, logger)
    control.upsert(request)
  }

  class UpsertToRelationalTables(
    connection: Connection,
    usageContext: UsageContext,
    logger: Logger
  ) {

    def upsert(request: StoreToDbRequest): SyncSuccess = transaction {
      val format = request.history.format
      val formatClassName = format.getClass.getName
      val historyTyped = request.history.toTyped

      val (finalFormat, mergedHistoryJson) = upsertHistory(formatClassName, historyTyped)

      // Count events in the new history
      val eventCount = historyTyped.events.values.size
      val feedbackCount = historyTyped.gradingHistory.gradingMap.map { case (_, group) =>
        group.graderResultMap.size
      }.sum + historyTyped.scaffoldingHistory.scaffoldingMap.size

      SyncSuccess(0, eventCount + feedbackCount, 0, LocalDateTime.now())
    }

    private def upsertHistory(formatClassName: String, historyTyped: InteractionVariableHistory[_ <: InteractionFormat]): (String, String) = {
      val existing = query(
        "SELECT format, history_json FROM single_blob_history WHERE program_id=? AND scenario_id=? AND user_id=? AND variable_id=?",
        List(usageContext.programId, usageContext.scenarioId, usageContext.userId, request.syncContext.keyForSerialisation)
      ) { row =>
        row.getString("format") -> row.getString("history_json")
      }.headOption

      existing match {
        case Some((existingFormat, existingJson)) =>
          val existingFormatObj = InteractionFormat.fromClassName(existingFormat)
          val existingHistoryTyped = InteractionHistoryJson(existingFormatObj, existingJson).toTyped
          val mergedTyped = existingHistoryTyped.mergeWith(historyTyped)
          (existingFormat, existingFormatObj.historySerializer.serialize(mergedTyped))
        case None =>
          (formatClassName, format.historySerializer.serialize(historyTyped))
      }
    }

    // ... rest of the implementation
  }
}

/** Handles upsert operations for single-blob database format. */
object UpsertToSingleBlobDatabase {

  def handleRequest(connection: Connection, request: StoreToDbRequest, logger: Logger): SyncSuccess = {
    val control = new UpsertToSingleBlobDatabase(connection, request.usageContext, logger)
    control.upsertIntoSingleBlobTable(request)
  }

  class UpsertToSingleBlobDatabase(
    connection: Connection,
    usageContext: UsageContext,
    logger: Logger
  ) {

    /** Stores single-blob history with merge semantics. */
    def upsertIntoSingleBlobTable(request: StoreToDbRequest): SyncSuccess = transaction {
      val format = request.history.format
      val formatClassName = format.getClass.getName
      val historyTyped = request.history.toTyped

      // Read existing history for this sync context
      val existing = query(
        "SELECT format, history_json FROM single_blob_history WHERE program_id=? AND scenario_id=? AND user_id=? AND variable_id=?",
        List(request.syncContext.programId, request.syncContext.scenarioId, request.syncContext.userId, request.syncContext.keyForSerialisation)
      ) { row =>
        row.getString("format") -> row.getString("history_json")
      }.headOption

      val (finalFormat, mergedHistoryJson) = existing match {
        case Some((existingFormat, existingJson)) =>
          // Merge existing history with new history using typed operations
          val existingFormatObj = InteractionFormat.fromClassName(existingFormat)
          val existingHistoryTyped = InteractionHistoryJson(existingFormatObj, existingJson).toTyped
          // Merge the typed histories
          val mergedTyped = existingHistoryTyped.mergeWith(historyTyped)
          // Serialize the merged history
          (existingFormat, existingFormatObj.historySerializer.serialize(mergedTyped))
        case None =>
          // No existing history, use the new history
          (formatClassName, format.historySerializer.serialize(historyTyped))
      }

      // Insert or update the single blob history (store class name as string, not JSON)
      update(
        "INSERT INTO single_blob_history (program_id,scenario_id,user_id,variable_id,format,history_json) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE history_json=VALUES(history_json),format=VALUES(format)",
        request.syncContext.programId, request.syncContext.scenarioId, request.syncContext.userId, request.syncContext.keyForSerialisation, finalFormat, mergedHistoryJson
      )

      // Count events in the new history
      val eventCount = historyTyped.events.values.size
      val feedbackCount = historyTyped.gradingHistory.gradingMap.map { case (_, group) =>
        group.graderResultMap.size
      }.sum + historyTyped.scaffoldingHistory.scaffoldingMap.size

      SyncSuccess(0, eventCount + feedbackCount, 0, LocalDateTime.now())
    }

    private def readFormat(formatClassName: String): Option[InteractionFormat] = {
      InteractionFormat.fromClassName(formatClassName)
    }

    private def transaction[A](f: => A): A = {
      connection.setAutoCommit(false)
      try {
        val result = f
        connection.commit()
        result
      } catch {
        case e: Exception =>
          connection.rollback()
          throw e
      } finally {
        connection.setAutoCommit(true)
      }
    }

    private def query[A](sql: String, params: List[String])(f: java.sql.ResultSet => A): List[A] = {
      Using.resource(connection.prepareStatement(sql)) { stmt =>
        params.zipWithIndex.foreach { case (param, idx) =>
          stmt.setString(idx + 1, param)
        }
        Using.resource(stmt.executeQuery()) { rs =>
          List.newBuilder[A].++=(() => rs).toList
        }
      }
    }

    private def query[A](sql: String, params: List[String])(f: java.sql.ResultSet => (A, A)): List[(A, A)] = {
      Using.resource(connection.prepareStatement(sql)) { stmt =>
        params.zipWithIndex.foreach { case (param, idx) =>
          stmt.setString(idx + 1, param)
        }
        Using.resource(stmt.executeQuery()) { rs =>
          List.newBuilder[(A, A)].++=(() => rs).toList
        }
      }
    }

    private def update(sql: String, params: String*): Int = {
      Using.resource(connection.prepareStatement(sql)) { stmt =>
        params.zipWithIndex.foreach { case (param, idx) =>
          stmt.setString(idx + 1, param)
        }
        stmt.executeUpdate()
      }
    }

  }
}
