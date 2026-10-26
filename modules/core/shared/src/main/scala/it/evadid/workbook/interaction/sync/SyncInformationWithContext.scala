package it.evadid.workbook.interaction.sync

import it.evadid.core.datastructures.storage.RemoteCacheCollection.CacheKey
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.{RemoteDataReader, RemoteDataWriter}
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.model.SyncDirection.CLIENT_TO_SERVER
import it.evadid.workbook.interaction.sync.model.{DataEntryToSync, FetchResponse}
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory, InteractionVariableUntyped}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class SyncInformationWithContext(syncInformation: SyncInformation, usageContext: UsageContext) extends CacheKey[SyncContext, String] {

  private given ec: ExecutionContext = ExecutionContext.global

  def informAboutContextSwitch(): Future[SyncSuccess] = {
    println("[UGLY WARN in SYNCINFORMATION]: context switch detected, currently not clearing any values")
    Future.successful(SyncSuccess(0, 0, 0, LocalDateTime.now()))
    //if (!syncSource.shouldBePersistant()) syncSource.clearAllValues(usageContext) else Future.successful(SyncSuccess(0, 0, 0, LocalDateTime.now()))
  }

  def fetchAllFrom(logger: SyncLogger): Future[FetchResponse[SyncContext, String]] = {
    syncInformation.syncDestination.fetchAll(logger, usageContext)
  }

  def dataToStore[T, GA, SA](variable: InteractionVariable[T, GA, SA]): List[DataEntryToSync[SyncContext, String]] = {
    val historyToStore = variable.history.filterWithStrategy(syncInformation.syncStrategy)
    val syncContext = usageContext.toSyncContext(variable.keyForSerialization)
    if (historyToStore.events.isEmpty) List()
    else List(DataEntryToSync(syncContext, variable.serializeHistory(historyToStore), historyToStore.latestChangeTimestamp.get, CLIENT_TO_SERVER))
  }

  lazy val reader: RemoteDataReader[SyncContext, String] = new RemoteDataReader[SyncContext, String]() {

    override def fetchByKey(logger: SyncLogger, key: SyncContext): Future[FetchResponse[SyncContext, String]] = try {
      syncInformation.syncDestination.fetchFrom(logger, key)
    } catch case (e: Throwable) => {
      logger.logExceptionWarn(s"Error while calling SyncLogger::fetchByKey, ignoring data of ${key} from ${syncInformation.syncDestination.toString}", e)
      Future.failed(e)
    }

    override def fetchAll(logger: SyncLogger): Future[FetchResponse[SyncContext, String]] = try {
      fetchAllFrom(logger)
    } catch case (e: Throwable) => {
      logger.logExceptionWarn(s"Error while calling SyncLogger::fetchAllFrom, ignoring data from ${syncInformation.syncDestination.toString}", e)
      Future.failed(e)
    }
  }

  lazy val writer: RemoteDataWriter[SyncContext, String] = new RemoteDataWriter[SyncContext, String]() {
    override def writeForKey(logger: SyncLogger, key: SyncContext, dataValue: String): Future[SyncSuccess] = try {
      syncInformation.syncDestination.storeTo(logger, key, dataValue)
    } catch case (e: Throwable) => {
      logger.logExceptionWarn(s"Could not create future, ignoring write for key ${key} to ${syncInformation.syncDestination.toString}", e)
      Future.failed(e)
    }

    private def writeAllRec(logger: SyncLogger, seq: List[(SyncContext, String)]): Future[SyncSuccess] = {
      if (seq.isEmpty) Future.successful(SyncSuccess(0, 0, 0, LocalDateTime.now()))
      else writeForKey(logger, seq.head._1, seq.head._2).flatMap(headSuccess => writeAllRec(logger, seq.tail).map(headSuccess -> _)).map(tup => tup._1.combine(tup._2))
    }

    override def writeAll(logger: SyncLogger, map: Map[SyncContext, String]): Future[SyncSuccess] = try {
      writeAllRec(logger, map.iterator.toList)
    } catch case (e: Throwable) => {
      logger.logExceptionWarn(s"Could not create future, ignoring write to ${syncInformation.syncDestination.toString}", e)
      Future.failed(e)
    }
  }
}
