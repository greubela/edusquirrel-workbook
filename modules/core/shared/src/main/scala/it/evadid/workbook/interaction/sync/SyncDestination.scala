package it.evadid.workbook.interaction.sync

import it.evadid.workbook.interaction.variable.InteractionVariableHistorySerialized
import SyncFormatter.InteractionSyncRequest
import SyncInformation.SyncSuccess
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.FetchResponse

import scala.concurrent.{ExecutionContext, Future}

trait SyncDestination {

  type BackendServerResult[T] = Either[Throwable, T]

  protected def debug: Boolean = false

  def shouldBePersistant(): Boolean

  def fetchAll(context: UsageContext, formatter: SyncFormatter): Future[FetchResponse[SyncContext, InteractionVariableHistorySerialized]]

  def storeTo(context: SyncContext, request: InteractionVariableHistorySerialized, formatter: SyncFormatter): Future[SyncSuccess]

  def fetchFrom(context: SyncContext, formatter: SyncFormatter)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, InteractionVariableHistorySerialized]] = fetchAll(context.toUsageContext, formatter)

  def clearValues(context: SyncContext): Future[SyncSuccess]

  def clearAllValues(context: UsageContext): Future[SyncSuccess]

}


