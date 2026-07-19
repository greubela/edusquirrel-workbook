package it.evadid.workbook.interaction.sync

import it.evadid.workbook.interaction.variable.InteractionVariableHistorySerialized
import SyncFormatter.InteractionSyncRequest
import SyncInformation.SyncSuccess
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.FetchResponse
import it.evadid.util.logging.derived.SyncLogger

import scala.concurrent.{ExecutionContext, Future}

trait SyncDestination {

  type BackendServerResult[T] = Either[Throwable, T]

  protected def debug: Boolean = false

  def shouldBePersistant(): Boolean

  def isLocal: Boolean

  def fetchAll(logger: SyncLogger, context: UsageContext, formatter: SyncFormatter): Future[FetchResponse[SyncContext, InteractionVariableHistorySerialized]]

  def storeTo(logger: SyncLogger, context: SyncContext, request: InteractionVariableHistorySerialized, formatter: SyncFormatter): Future[SyncSuccess]

  def fetchFrom(logger: SyncLogger, context: SyncContext, formatter: SyncFormatter)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, InteractionVariableHistorySerialized]] = fetchAll(logger, context.toUsageContext, formatter)

  def clearValues(logger: SyncLogger, context: SyncContext): Future[SyncSuccess]

  def clearAllValues(logger: SyncLogger, context: UsageContext): Future[SyncSuccess]

}


