package it.evadid.workbook.model.interaction.sync

import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.model.interaction.variable.InteractionVariable

import java.time.LocalDateTime

trait SyncControl {

  def requestFetch(interactionVariable: InteractionVariable[?], maxCacheAge: LocalDateTime = LocalDateTime.now()): Unit

  def requestStore[T](from: InteractionVariable[T], forcePush: Boolean): Unit

  def syncLogger: SyncLogger

}
