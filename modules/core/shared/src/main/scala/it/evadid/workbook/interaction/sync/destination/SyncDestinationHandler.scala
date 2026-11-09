package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncSuccess
import it.evadid.workbook.interaction.variable.InteractionVariableHistory

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import upickle.default.*
trait SyncDestinationHandler {

  def clearAll(logger: SyncLogger)(using ec: ExecutionContext): Future[SyncSuccess]

  def removeKeyRaw(logger: SyncLogger, key: String)(using ec: ExecutionContext): Future[SyncSuccess]

  def storeElementRaw(logger: SyncLogger, key: String, value: String)(using ec: ExecutionContext): Future[SyncSuccess]

  def readElementRaw(logger: SyncLogger, key: String)(using ec: ExecutionContext): Future[String]

  def readDestinationRaw(logger: SyncLogger)(implicit ec: ExecutionContext): Future[Map[String, String]]

  def underlyingDefinition: SyncDestinationDefinition

  def withSyncLogger(logger: SyncLogger): SyncDestinationHandlerBasic = SyncDestinationHandlerBasic(this, logger)

  def forHistory(logger: SyncLogger): SyncDestinationHistoryHandler = withSyncLogger(logger).forHistory()
}
