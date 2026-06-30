package it.evadid.workbook.model.interaction.sync

import it.evadid.workbook.model.interaction.sync.SyncFormatter.InteractionSyncRequest
import it.evadid.workbook.model.interaction.sync.SyncInformation.SyncSuccess
import it.evadid.workbook.model.interaction.variable.InteractionVariableHistorySerialized

import scala.concurrent.{ExecutionContext, Future}

trait SyncDestination {

  type BackendServerResult[T] = Either[Throwable, T]

  protected def debug: Boolean = false

  /*def syncTo(key: String, value: String): Future[SyncSuccess]

  def clearValues(key: String): Future[SyncSuccess]

  def fetchFrom(key: String): Future[String]*/

  def shouldBePersistant(): Boolean

  def fetchAll(context: UsageContext): Future[Map[SyncContext, String]]

  def storeTo(context: SyncContext, request: InteractionVariableHistorySerialized, formatter: SyncFormatter): Future[SyncSuccess]

  def fetchFrom(context: SyncContext)(implicit ec: ExecutionContext): Future[Option[String]] = fetchAll(context.toUsageContext).map(_.get(context))(using ec)

  def clearValues(context: SyncContext): Future[SyncSuccess]

  def clearAllValues(context: UsageContext): Future[SyncSuccess]

}


