package it.evadid.server.commandHandler.sql

import it.evadid.util.Logger
import it.evadid.workbook.model.interaction.sync.SyncFormatter.RichInteractionVariableFormatter
import it.evadid.workbook.model.interaction.sync.SyncInformation.SyncSuccess
import it.evadid.workbook.model.interaction.sync.UsageContext

import java.sql.Connection
import java.time.LocalDateTime

case class DeleteInDatabase(
                             connection: Connection,
                             context: UsageContext,
                             logger: Logger,
                             formatter: RichInteractionVariableFormatter
                           ) {


  private def deleteEventsById(conn: Connection, ids: Set[Long], logger: Logger): SyncSuccess = {
    logger.logInfo(s"deleting ${ids.size} events from database")
    if (ids.isEmpty) {
      SyncSuccess(0, 0, 0, LocalDateTime.now())
    }
    else {
      val placeholders = ids.map(_ => "?").mkString(", ")
      val stmt = conn.prepareStatement(s"DELETE FROM `events` WHERE `eventid` IN ($placeholders)")
      try {
        ids.zipWithIndex.foreach { case (id, index) => stmt.setLong(index + 1, id) }
        val nr = stmt.executeUpdate()
        println(s"deleted $nr events!")
        SyncSuccess(0, 0, nr, LocalDateTime.now())
      } catch {
        case e: Exception => logger.logError(s"Error clearing database sync events by id: ${e.getMessage}")
          throw e
      } finally {
        stmt.close()
      }
    }
  }

}
