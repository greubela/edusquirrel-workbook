package it.evadid.workbook.interaction.sync

import upickle.default.ReadWriter
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.core.datastructures.storage.RemoteCacheCollection.CacheKey
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
import it.evadid.core.datastructures.user.AllUserInfo
import it.evadid.core.util.io.SerializableWithCompanion.{GenericSerializableFactory, SerializableWithGenericFactory}
import it.evadid.core.util.io.{SerializableWithCompanion, Serializer, TypeConverter}
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.destination.SyncDestinationHistory
import it.evadid.workbook.interaction.sync.model.DataEntryToSync.*
import it.evadid.workbook.interaction.sync.model.FetchResponse
import it.evadid.workbook.interaction.variable.*

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import upickle.default.*

case class SyncInformation(
                            syncDestination: SyncDestinationHistory,
                            syncStrategy: SyncStrategy
                          ) {

  def forContext(context: UsageContext): SyncInformationWithContext = SyncInformationWithContext(this, context)

}



