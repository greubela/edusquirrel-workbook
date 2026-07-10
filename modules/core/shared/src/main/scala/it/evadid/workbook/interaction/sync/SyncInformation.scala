package it.evadid.workbook.interaction.sync

import it.evadid.core.datastructures.storage.RemoteCacheCollection.CacheKey
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.interaction.sync.SyncInformation.SyncInformationWithContext
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory, InteractionVariableHistorySerialized}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

case class SyncInformation(syncSource: SyncDestination, syncStrategy: SyncStrategy, formatter: SyncFormatter) {

  def forContext(context: UsageContext): SyncInformationWithContext = SyncInformationWithContext(syncSource, syncStrategy, formatter, context)

}

object SyncInformation {

  case class InteractionVariableFetchResponse(timestampFetchResponse: LocalDateTime, fetchedValues: Set[DataEntryReadFromServer[SyncContext, InteractionVariableHistorySerialized]]) extends FetchResponse[SyncContext, InteractionVariableHistorySerialized] {

  }

  case class SyncSuccess(elementsAdded: Int, elementsChanged: Int, elementsRemoved: Int, timestampCommitted: LocalDateTime) {
    def combine(other: SyncSuccess): SyncSuccess = {
      val later = if (timestampCommitted.isBefore(other.timestampCommitted)) other.timestampCommitted else timestampCommitted
      SyncSuccess(elementsAdded + other.elementsAdded, elementsChanged + other.elementsChanged, elementsRemoved + other.elementsRemoved, later)
    }
  }

  object SyncSuccess {
    def emptyNow(): SyncSuccess = SyncSuccess(0, 0, 0, LocalDateTime.now())
  }

  case class SyncFetchedHistory[T](typedElements: InteractionVariableHistory[T], fetchedAt: LocalDateTime, unparsableElements: InteractionVariableHistorySerialized)

  case class SyncCache(createdAt: LocalDateTime, createdForContext: UsageContext, contextMaps: Map[SyncContext, InteractionVariableHistorySerialized]) {

    def typedHistory[T](key: String, serializer: Serializer[T]): SyncFetchedHistory[T] = {
      typedHistory(createdForContext.toSyncContext(key), serializer)
    }

    def typedHistory[T](context: SyncContext, serializer: Serializer[T]): SyncFetchedHistory[T] = {
      val historySerialized: InteractionVariableHistorySerialized = contextMaps.getOrElse(context, InteractionVariableHistorySerialized.empty)
      val (success, failed) = historySerialized.tryDeserialize(serializer)
      SyncFetchedHistory(success, createdAt, failed)
    }

  }

  case class SyncInformationWithContext(syncSource: SyncDestination, syncStrategy: SyncStrategy, formatter: SyncFormatter, usageContext: UsageContext) extends CacheKey[SyncContext, InteractionVariableHistorySerialized] {

    private given ec: ExecutionContext = ExecutionContext.global

    def informAboutContextSwitch(): Future[SyncSuccess] = {
      println("[UGLY WARN in SYNCINFORMATION]: context switch detected, currently not clearing any values")
      Future.successful(SyncSuccess(0, 0, 0, LocalDateTime.now()))
      //if (!syncSource.shouldBePersistant()) syncSource.clearAllValues(usageContext) else Future.successful(SyncSuccess(0, 0, 0, LocalDateTime.now()))
    }

    def fetchAllFrom(): Future[InteractionVariableFetchResponse] = {
      syncSource.fetchAll(usageContext, formatter).map(response => {
        InteractionVariableFetchResponse(response.timestampFetchResponse, response.fetchedValues)
      })
    }

    def dataToStore[T](variable: InteractionVariable[T]): List[DataEntryToWriteToServer[SyncContext, InteractionVariableHistorySerialized]] = {
      val historySerialized = variable.history.serializedWithStrategy(syncStrategy, variable.underlyingInteraction.serializer)
      val syncContext = usageContext.toSyncContext(variable.keyForSerialization)
      if (historySerialized.states.isEmpty) List()
      else List(DataEntryToWriteToServer(syncContext, historySerialized, historySerialized.lastStateOption.map(_.timestamp).get))
    }

    lazy val reader: RemoteDataReader[SyncContext, InteractionVariableHistorySerialized] = new RemoteDataReader[SyncContext, InteractionVariableHistorySerialized]() {
      override def fetchByKey(key: SyncContext): Future[FetchResponse[SyncContext, InteractionVariableHistorySerialized]] = fetchAllFrom()

      override def fetchAll(): Future[FetchResponse[SyncContext, InteractionVariableHistorySerialized]] = fetchAllFrom()
    }

    lazy val writer: RemoteDataWriter[SyncContext, InteractionVariableHistorySerialized] = new RemoteDataWriter[SyncContext, InteractionVariableHistorySerialized]() {
      override def writeForKey(key: SyncContext, dataValue: InteractionVariableHistorySerialized): Future[SyncSuccess] = syncSource.storeTo(key, dataValue, formatter)

      private def writeAllRec(seq: List[(SyncContext, InteractionVariableHistorySerialized)]): Future[SyncSuccess] = {
        if (seq.isEmpty) Future.successful(SyncSuccess(0, 0, 0, LocalDateTime.now()))
        else writeForKey(seq.head._1, seq.head._2).flatMap(headSuccess => writeAllRec(seq.tail).map(headSuccess -> _)).map(tup => tup._1.combine(tup._2))
      }

      override def writeAll(map: Map[SyncContext, InteractionVariableHistorySerialized]): Future[SyncSuccess] = {
        writeAllRec(map.iterator.toList)
      }
    }


  }


}


