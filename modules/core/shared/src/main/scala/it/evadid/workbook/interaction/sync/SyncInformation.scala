package it.evadid.workbook.interaction.sync

import it.evadid.core.datastructures.storage.RemoteCacheCollection.CacheKey
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
import it.evadid.core.datastructures.user.AllUserInfo
import it.evadid.core.util.io.SerializableWithCompanion.{GenericSerializableFactory, SerializableWithGenericFactory}
import it.evadid.core.util.io.{SerializableWithCompanion, Serializer, TypeConverter}
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.destination.SyncDestinationHistory
import it.evadid.workbook.interaction.variable.{InteractionVariable, InteractionVariableHistory, InteractionVariableHistorySerialized}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}


case class SyncInformation(
                            syncDestination: SyncDestinationHistory,
                            syncStrategy: SyncStrategy,
                            formatter: SyncFormatter
                          )  {

  def forContext(context: UsageContext): SyncInformationWithContext = SyncInformationWithContext(this, context)

}

object SyncInformation {

  case class InteractionVariableFetchResponse(timestampFetchResponse: LocalDateTime, fetchedValues: Set[DataEntryReadFromServer[SyncContext, InteractionVariableHistorySerialized]]) extends FetchResponse[SyncContext, InteractionVariableHistorySerialized] {

  }
  
  case class SyncFetchedHistory[T](typedElements: InteractionVariableHistory[T], fetchedAt: LocalDateTime, unparsableElements: InteractionVariableHistorySerialized)



 


}


