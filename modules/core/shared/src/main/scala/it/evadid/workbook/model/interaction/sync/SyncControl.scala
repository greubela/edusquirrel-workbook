package it.evadid.workbook.model.interaction.sync

import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.model.interaction.variable.InteractionVariable

import java.time.LocalDateTime
import scala.concurrent.Future

trait SyncControl {

  def requestStore[T](from: InteractionVariable[T], forcePush: Boolean, requestTime: LocalDateTime = LocalDateTime.now()): Future[?] = requestStore(List(from), forcePush, requestTime)

  def requestStore(from: List[InteractionVariable[?]], forcePush: Boolean, requestTime: LocalDateTime): Future[?]

  def requestFetch(variables: List[InteractionVariable[?]], requestTime: LocalDateTime): Future[?]

  def requestFetch(interactionVariable: InteractionVariable[?], requestTime: LocalDateTime = LocalDateTime.now()): Future[?]

  def syncLogger: SyncLogger

}
