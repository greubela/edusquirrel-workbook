package it.evadid.workbook.sync.destination

import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.sync.SyncSuccess

import scala.concurrent.{ExecutionContext, Future}
import upickle.default.*
/** Raw key-value destination; results name the affected keys. */
trait SyncDestinationHandler {

  def clearAll(logger: SyncLogger)(using ec: ExecutionContext): Future[SyncSuccess[String]]

  def removeKeyRaw(logger: SyncLogger, key: String)(using ec: ExecutionContext): Future[SyncSuccess[String]]

  def storeElementRaw(logger: SyncLogger, key: String, value: String)(using ec: ExecutionContext): Future[SyncSuccess[String]] =
    updateElementRaw(logger, key, previous => value -> SyncSuccess.written(key, previous, value))

  /** Atomically replace one value using its current contents; no asynchronous gap inside the update.
    * The update also computes the result, which is returned once the write has committed.
    */
  def updateElementRaw[R](logger: SyncLogger, key: String, update: Option[String] => (String, R))(using ec: ExecutionContext): Future[R]

  def readElementRaw(logger: SyncLogger, key: String)(using ec: ExecutionContext): Future[String]

  def readDestinationRaw(logger: SyncLogger)(implicit ec: ExecutionContext): Future[Map[String, String]]

  def underlyingDefinition: SyncDestinationDefinition

  def withSyncLogger(logger: SyncLogger): SyncDestinationHandlerBasic = SyncDestinationHandlerBasic(this, logger)

  def forHistory(logger: SyncLogger): SyncDestinationHistoryHandler = withSyncLogger(logger).forHistory()
}
