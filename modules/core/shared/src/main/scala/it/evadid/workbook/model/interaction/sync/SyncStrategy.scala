package it.evadid.workbook.model.interaction.sync

import it.evadid.workbook.model.interaction.variable.InteractionVariableState


sealed trait SyncStrategy {

  def selectEventsToSync[T](events: Set[InteractionVariableState[T]]): Set[InteractionVariableState[T]]

}

object SyncStrategy {

  object SYNC_EVERYTHING extends SyncStrategy {
    override def selectEventsToSync[T](events: Set[InteractionVariableState[T]]): Set[InteractionVariableState[T]] = events
  }

  private case class DesiredRelevance(desired: List[UpdateImportance]) extends SyncStrategy {
    override def selectEventsToSync[T](events: Set[InteractionVariableState[T]]): Set[InteractionVariableState[T]] = events.filter(e => desired.contains(e.updateImportance))
  }

  val SYNC_MAJOR: SyncStrategy = DesiredRelevance(List(UpdateImportance.MAJOR))
  val SYNC_MINOR: SyncStrategy = DesiredRelevance(List(UpdateImportance.MINOR, UpdateImportance.MAJOR))

}

