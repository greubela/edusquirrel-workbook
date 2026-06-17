package it.evadid.workbook.model.interaction.sync

import it.evadid.workbook.model.interaction.variable.InteractionVariableState


sealed trait SyncStrategy {

  def selectEventsToSync[T](events: Set[InteractionVariableState[T]]): Set[InteractionVariableState[T]]

}

object SyncStrategy {

  object SYNC_EVERYTHING extends SyncStrategy {
    override def selectEventsToSync[T](events: Set[InteractionVariableState[T]]): Set[InteractionVariableState[T]] = events
  }

}

