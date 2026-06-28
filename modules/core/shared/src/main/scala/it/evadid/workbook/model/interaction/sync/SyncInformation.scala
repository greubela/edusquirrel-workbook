package it.evadid.workbook.model.interaction.sync

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.sync.SyncFormatter.InteractionSyncRequest
import it.evadid.workbook.model.interaction.sync.SyncInformation.SyncInformationWithContext
import it.evadid.workbook.model.interaction.variable.{InteractionVariableHistory, InteractionVariableHistorySerialized}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class SyncInformation(syncSource: SyncDestination, syncStrategy: SyncStrategy, formatter: SyncFormatter) {

  def forContext(context: UsageContext): SyncInformationWithContext = SyncInformationWithContext(syncSource, syncStrategy, formatter, context)

}

object SyncInformation {

  case class SyncSuccess(elementsAdded: Int, elementsChanged: Int, elementsRemoved: Int, timestampCommitted: LocalDateTime)

  case class SyncFetchedHistory[T](typedElements: InteractionVariableHistory[T], fetchedAt: LocalDateTime, unparsableElements: InteractionVariableHistorySerialized)

  case class SyncCache(createdAt: LocalDateTime, createdForContext: UsageContext, values: Map[SyncContext, InteractionVariableHistorySerialized]) {

    def typedHistory[T](key: String, serializer: Serializer[T]): SyncFetchedHistory[T] = {
      typedHistory(createdForContext.toSyncContext(key), serializer)
    }

    def typedHistory[T](context: SyncContext, serializer: Serializer[T]): SyncFetchedHistory[T] = {
      val historySerialized: InteractionVariableHistorySerialized = values.getOrElse(context, InteractionVariableHistorySerialized.empty)
      val (success, failed) = historySerialized.tryDeserialize(serializer)
      SyncFetchedHistory(success, createdAt, failed)
    }

  }

  case class SyncInformationWithContext(syncSource: SyncDestination, syncStrategy: SyncStrategy, formatter: SyncFormatter, usageContext: UsageContext) {

    private given ec: ExecutionContext = ExecutionContext.global

    def storeTo[T](key: String, history: InteractionVariableHistory[T], serializer: Serializer[T]): Future[SyncSuccess] = {
      val syncContext: SyncContext = usageContext.toSyncContext(key)
      val historySerialized: InteractionVariableHistorySerialized = history.serializedWithStrategy(syncStrategy, serializer)
      val diff = history.events.size - historySerialized.states.size
      println(s"Syncing $key to ${syncSource} with strategy $syncStrategy, diff of $diff")
      syncSource.syncTo(syncContext, InteractionSyncRequest(syncContext, historySerialized), formatter)
    }

    def informAboutContextSwitch(): Future[SyncSuccess] = if (!syncSource.shouldBePersistant()) syncSource.clearAllValues(usageContext) else Future.successful(SyncSuccess(0, 0, 0, LocalDateTime.now()))

    def tryParseValue(key: SyncContext, value: String): Option[(SyncContext, InteractionVariableHistorySerialized)] = {
      formatter.tryDeserialize(value).map(typedVal => key -> typedVal)
    }

    def fetchAllFrom(useTimestampForCache: LocalDateTime = LocalDateTime.now()): Future[SyncCache] = {
      println(s"Fetching all values from ${syncSource} for context $usageContext")
      val valuesUntyped: Future[Map[SyncContext, String]] = syncSource.fetchAll(usageContext)
      valuesUntyped.onComplete{
        case Success(value) => println(s"Fetched values: $value")
        case Failure(e) => println(s"Failed to fetch values: $e")
      }
      val valuesHistory: Future[Map[SyncContext, InteractionVariableHistorySerialized]] = valuesUntyped.map(_.flatMap(tup => tryParseValue(tup._1, tup._2).toMap))
      valuesHistory.map((resMap: Map[SyncContext, InteractionVariableHistorySerialized]) => SyncCache(useTimestampForCache, usageContext, resMap))
    }

  }


}


