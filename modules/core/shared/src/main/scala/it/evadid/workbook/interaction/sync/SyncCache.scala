package it.evadid.workbook.interaction.sync

import upickle.default.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.interaction.sync.SyncInformation.*
import it.evadid.workbook.interaction.variable.*
import it.evadid.workbook.interaction.variable.InteractionVariableHistory.InteractionVariableHistoryUntyped

import java.time.LocalDateTime

case class SyncCache(createdAt: LocalDateTime, createdForContext: UsageContext, contextMaps: Map[SyncContext, String]) derives ReadWriter {
/*
  def typedHistory[T](key: String, serializer: Serializer[T]): SyncFetchedHistory[T] = {
    typedHistory(createdForContext.toSyncContext(key), serializer)
  }

  def typedHistory[T](context: SyncContext, serializer: Serializer[T]): SyncFetchedHistory[T] = {
    val historySerialized: InteractionVariableHistorySerialized = contextMaps.getOrElse(context, InteractionVariableHistorySerialized.empty)
    val (success, failed) = historySerialized.tryDeserialize(serializer)
    SyncFetchedHistory(success, createdAt, failed)
  }
*/
}
