package it.evadid.homepage.workbook.syncDestination

import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionInfo.ExecutionSuccess
import it.evadid.distribution.commandTypes.SQLCommands
import it.evadid.distribution.commandTypes.SQLCommands.DbFetchResponse
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.destination.*
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.SyncDestinationBasedOnHistoryDatabase
import it.evadid.workbook.interaction.sync.model.{FetchResponse, InteractionHistoryJson}

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.command.SerializedException

import java.time.LocalDateTime
import scala.concurrent.*

/** `signedToken` is the one `backend` sends: the backend stores, fetches and deletes histories only for a verified
  * account, so without a valid token every request fails here instead of being sent.
  */
case class DatabaseSyncViaBackendServerHandler(pSyncLogger: SyncLogger, definition: SyncDestinationBasedOnHistoryDatabase,
    backend: ExecutionClient, databaseConfig: it.evadid.sql.DatabaseConfiguration, signedToken: Option[SignedToken])
  extends SyncDestinationHistoryHandler {

  private given ec: ExecutionContext = ExecutionContext.global

  private def signedIn[A](operation: String)(request: => Future[A]): Future[A] =
    signedToken match {
      case Some(token) if token.info.expiresAt.isAfter(LocalDateTime.now()) => request
      case Some(token) => Future.failed(SerializedException(
        s"Cannot $operation histories in the backend database: the session expired at ${token.info.expiresAt}; sign in again."))
      case None => Future.failed(SerializedException(
        s"Cannot $operation histories in the backend database without a signed-in account."))
    }

  override def syncLogger: SyncLogger = pSyncLogger

  private def clearValues(context: UsageContext, limitToKey: Option[String]): Future[SyncSuccess] = {
    val request = SQLCommands.DeleteInDbRequest(context, limitToKey, databaseConfig, definition.format)
    signedIn("delete")(SQLCommands.clearValuesDbCommand.sendCommandTo(backend, request).map(_.result))
  }

  override def clearAllHistories(context: UsageContext)(using ExecutionContext): Future[SyncSuccess] = {
    val request = SQLCommands.DeleteInDbRequest(context, None, databaseConfig, definition.format, allWorkbooks = true)
    signedIn("reset")(SQLCommands.resetUserHistoriesDbCommand.sendCommandTo(backend, request).map(_.result))
  }

  override def clearValue(context: SyncContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = {
    clearValues(context.toUsageContext, Some(context.keyForSerialisation))
  }

  override def clearValues(context: UsageContext)(implicit ec: ExecutionContext): Future[SyncSuccess] = clearValues(context, None)


  override def storeHistoryJson(context: SyncContext, json: String)(using ExecutionContext): Future[SyncSuccess] =
    storeHistoriesJson(Map(context -> json))

  override def storeHistoriesJson(values: Map[SyncContext, String])(using ExecutionContext): Future[SyncSuccess] = {
    if values.isEmpty then Future.successful(SyncSuccess.emptyNow()) else {
      val entries = values.toSeq.map((context, json) => context -> InteractionHistoryJson.deserialize(json))
      val request = SQLCommands.BulkStoreToDbRequest(entries.map(_._1), entries.map(_._2), databaseConfig, definition.format)
      signedIn("store")(SQLCommands.bulkStoreToDbCommand.sendCommandTo(backend, request).map(_.result))
    }
  }

  private def toFetchResponse(result: ExecutionSuccess[DbFetchResponse]): FetchResponse[SyncContext, String] =
    FetchResponse.fromMap(result.history.timestampExecutionFinished, result.result.fetchedElements.map((key, value) => key -> value.serialized))

  override def readHistoriesJson(usageContext: UsageContext)(using ec: ExecutionContext): Future[FetchResponse[SyncContext, String]] = {
    val request = SQLCommands.FetchEventsFromDbRequest(usageContext, Seq.empty, java.time.LocalDateTime.MIN,
      java.time.LocalDateTime.MAX, databaseConfig, definition.format, EventSyncStrategy.SYNC_EVERYTHING)
    signedIn("fetch")(SQLCommands.fetchEventsFromDbCommand.sendCommandTo(backend, request).map(toFetchResponse))
  }

  override def readHistoriesInRange(usageContext: UsageContext, keys: Seq[SyncContext], starting: java.time.LocalDateTime,
      until: java.time.LocalDateTime, strategy: EventSyncStrategy)(using ec: ExecutionContext): Future[Map[SyncContext, InteractionHistoryJson]] = {
    require(keys.forall(_.toUsageContext == usageContext), "Range keys must belong to the requested usage context")
    require(!until.isBefore(starting), "History range end must not precede its start")
    if keys.isEmpty then Future.successful(Map.empty) else {
      val request = SQLCommands.FetchEventsFromDbRequest(usageContext, keys.map(_.keyForSerialisation), starting, until,
        databaseConfig, definition.format, strategy)
      signedIn("fetch")(SQLCommands.fetchEventsFromDbCommand.sendCommandTo(backend, request).map(_.result.fetchedElements))
    }
  }
}
