package it.evadid.workbook.interaction.sync

import it.evadid.core.datastructures.storage.remote.RemoteReaderWriter
import it.evadid.core.datastructures.storage.remote.RemoteReaderWriter.{RemoteDataReader, RemoteDataWriter}
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.destination.{SyncDestinationDefinition, SyncDestinationHistoryHandler}
import it.evadid.workbook.interaction.sync.model.SyncDirection.CLIENT_TO_SERVER
import it.evadid.workbook.interaction.sync.model.{DataEntryToSync, FetchResponse}
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class SyncInformationWithContext(syncInformation: SyncInformation, usageContext: UsageContext, resolveDefinition: SyncDestinationDefinition => SyncDestinationHistoryHandler)
  extends RemoteReaderWriter[SyncContext, String] {

  private given ec: ExecutionContext = ExecutionContext.global

  val handler: SyncDestinationHistoryHandler = resolveDefinition(syncInformation.syncDestination)


  def serialize[T, GA, SA](interaction: InteractionVariable[T, GA, SA]): String = {
    val filtered = interaction.currentHistory.cleanWithStrategy(syncInformation.syncStrategy)
    interaction.serializerHistory.serialize(filtered)
  }

  lazy val reader: RemoteDataReader[SyncContext, String] = new RemoteDataReader[SyncContext, String]() {

    override def fetchByKey(logger: SyncLogger, key: SyncContext): Future[FetchResponse[SyncContext, String]] = try {
      handler.readHistoryJson(key)
    } catch case (e: Throwable) => {
      logger.logExceptionWarn(s"Error while calling SyncLogger::fetchByKey, ignoring data of ${key} from ${syncInformation.syncDestination.toString}", e)
      Future.failed(e)
    }

    override def fetchByKeys(logger: SyncLogger, keys: Set[SyncContext]): Future[FetchResponse[SyncContext, String]] =
      if keys.isEmpty then Future.successful(FetchResponse(LocalDateTime.now(), Set.empty))
      else fetchAll(logger).map(response => response.copy(fetchedValues = response.fetchedValues.filter(entry => keys(entry.dataKey))))

    override def fetchAll(logger: SyncLogger): Future[FetchResponse[SyncContext, String]] = try {
      handler.readHistoriesJson(usageContext)
    } catch case (e: Throwable) => {
      logger.logExceptionWarn(s"Error while calling SyncLogger::fetchAllFrom, ignoring data from ${syncInformation.syncDestination.toString}", e)
      Future.failed(e)
    }

    override def flush(logger: SyncLogger, forceFlush: Boolean): Unit = handler.flush(forceFlush)

  }

  lazy val writer: RemoteDataWriter[SyncContext, String] = new RemoteDataWriter[SyncContext, String]() {
    override def writeForKey(logger: SyncLogger, key: SyncContext, dataValue: String): Future[SyncSuccess] = try {
      handler.storeHistoryJson(key, dataValue)
    } catch case (e: Throwable) => {
      logger.logExceptionWarn(s"Could not create future, ignoring write for key ${key} to ${syncInformation.syncDestination.toString}", e)
      Future.failed(e)
    }

    override def writeAll(logger: SyncLogger, map: Map[SyncContext, String]): Future[SyncSuccess] =
      scala.util.Try(handler.storeHistoriesJson(map)).fold(Future.failed, identity)

    override def flush(logger: SyncLogger, forceFlush: Boolean): Unit = handler.flush(forceFlush)
  }
}
