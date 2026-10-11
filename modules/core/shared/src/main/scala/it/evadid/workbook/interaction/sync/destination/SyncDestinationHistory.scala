package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
import it.evadid.core.util.io.serializer.ConstructorLikeSerializable
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.model.FetchResponse
import it.evadid.workbook.interaction.sync.{SyncContext, SyncSuccess, UsageContext}
import it.evadid.workbook.interaction.variable.*
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.InteractionVariableHistory.InteractionVariableHistoryUntyped

import scala.concurrent.{ExecutionContext, Future}

trait SyncDestinationHistory extends SyncDestination {

  type BackendServerResult[T] = Either[Throwable, T]

  protected def debug: Boolean = false

  def shouldBePersistant(): Boolean

  def isLocal: Boolean

  def fetchAll(logger: SyncLogger, context: UsageContext): Future[FetchResponse[SyncContext, InteractionVariableHistoryUntyped]]

  def storeTo[T, GA, SA](logger: SyncLogger, context: SyncContext, request: InteractionVariableHistory[T, GA, SA]): Future[SyncSuccess]

  def fetchFrom(logger: SyncLogger, context: SyncContext)(implicit ec: ExecutionContext): Future[FetchResponse[SyncContext, InteractionVariableHistoryUntyped]] = fetchAll(logger, context.toUsageContext)

  def clearValues(logger: SyncLogger, context: SyncContext): Future[SyncSuccess]

  def clearAllValues(logger: SyncLogger, context: UsageContext): Future[SyncSuccess]

}