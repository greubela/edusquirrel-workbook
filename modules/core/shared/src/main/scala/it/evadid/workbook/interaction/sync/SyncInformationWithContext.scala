package it.evadid.workbook.interaction.sync

import it.evadid.core.datastructures.storage.RemoteCacheCollection.CacheKey
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.{DataEntryToWriteToServer, FetchResponse, RemoteDataReader, RemoteDataWriter}
import it.evadid.core.datastructures.user.AllUserInfo
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncInformation.*
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistorySerialized}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class SyncInformationWithContext(syncInformation: SyncInformation, usageContext: UsageContext) extends CacheKey[SyncContext, InteractionVariableHistorySerialized] {

  private given ec: ExecutionContext = ExecutionContext.global

  def informAboutContextSwitch(): Future[SyncSuccess] = {
    println("[UGLY WARN in SYNCINFORMATION]: context switch detected, currently not clearing any values")
    Future.successful(SyncSuccess(0, 0, 0, LocalDateTime.now()))
    //if (!syncSource.shouldBePersistant()) syncSource.clearAllValues(usageContext) else Future.successful(SyncSuccess(0, 0, 0, LocalDateTime.now()))
  }

  def fetchAllFrom(logger: SyncLogger): Future[InteractionVariableFetchResponse] = try {
    syncInformation.syncDestination.fetchAll(logger, usageContext, syncInformation.formatter).map(response => {
      InteractionVariableFetchResponse(response.timestampFetchResponse, response.fetchedValues)
    })
  } catch case (e: Throwable) => {
    logger.logExceptionWarn(s"Could not create future, ignoring read from ${syncInformation.syncDestination.toString}", e)
    Future.failed(e)
  }

  def dataToStore[T](variable: InteractionVariable[T]): List[DataEntryToWriteToServer[SyncContext, InteractionVariableHistorySerialized]] = {
    val historySerialized = variable.history.serializedWithStrategy(syncInformation.syncStrategy, variable.underlyingInteraction.serializerInteractionContent)
    val syncContext = usageContext.toSyncContext(variable.keyForSerialization)
    if (historySerialized.states.isEmpty) List()
    else List(DataEntryToWriteToServer(syncContext, historySerialized, historySerialized.lastStateOption.map(_.timestamp).get))
  }

  lazy val reader: RemoteDataReader[SyncContext, InteractionVariableHistorySerialized] = new RemoteDataReader[SyncContext, InteractionVariableHistorySerialized]() {

    override def fetchByKey(logger: SyncLogger, key: SyncContext): Future[FetchResponse[SyncContext, InteractionVariableHistorySerialized]] = try {
      fetchAllFrom(logger)
    } catch case (e: Throwable) => {
      logger.logExceptionWarn(s"Error while calling SyncLogger::fetchByKey, ignoring data of ${key} from ${syncInformation.syncDestination.toString}", e)
      Future.failed(e)
    }

    override def fetchAll(logger: SyncLogger): Future[FetchResponse[SyncContext, InteractionVariableHistorySerialized]] = try {
      fetchAllFrom(logger)
    } catch case (e: Throwable) => {
      logger.logExceptionWarn(s"Error while calling SyncLogger::fetchAllFrom, ignoring data from ${syncInformation.syncDestination.toString}", e)
      Future.failed(e)
    }
  }

  lazy val writer: RemoteDataWriter[SyncContext, InteractionVariableHistorySerialized] = new RemoteDataWriter[SyncContext, InteractionVariableHistorySerialized]() {
    override def writeForKey(logger: SyncLogger, key: SyncContext, dataValue: InteractionVariableHistorySerialized): Future[SyncSuccess] = try {
      syncInformation.syncDestination.storeTo(logger, key, dataValue, syncInformation.formatter)
    } catch case (e: Throwable) => {
      logger.logExceptionWarn(s"Could not create future, ignoring write for key ${key} to ${syncInformation.syncDestination.toString}", e)
      Future.failed(e)
    }

    private def writeAllRec(logger: SyncLogger, seq: List[(SyncContext, InteractionVariableHistorySerialized)]): Future[SyncSuccess] = {
      if (seq.isEmpty) Future.successful(SyncSuccess(0, 0, 0, LocalDateTime.now()))
      else writeForKey(logger, seq.head._1, seq.head._2).flatMap(headSuccess => writeAllRec(logger, seq.tail).map(headSuccess -> _)).map(tup => tup._1.combine(tup._2))
    }

    override def writeAll(logger: SyncLogger, map: Map[SyncContext, InteractionVariableHistorySerialized]): Future[SyncSuccess] = try {
      writeAllRec(logger, map.iterator.toList)
    } catch case (e: Throwable) => {
      logger.logExceptionWarn(s"Could not create future, ignoring write to ${syncInformation.syncDestination.toString}", e)
      Future.failed(e)
    }
  }
}
