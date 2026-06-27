package it.evadid.workbook.model.interaction.sync

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.variable.{InteractionVariable, InteractionVariableHistory}

import java.time.LocalDateTime

trait SyncControl {

  def requestFetch(interactionVariable: InteractionVariable[?], maxCacheAge: LocalDateTime = LocalDateTime.now()): Unit

  def requestStore[T](keyForSerialisation: String, history: InteractionVariableHistory[T], valueSerializer: Serializer[T], forceSyncNow: Boolean = false): Unit

}
