package it.evadid.workbook.model.interaction.sync

import it.evadid.workbook.model.interaction.variable.InteractionVariableHistory


sealed trait SyncStrategy {

  def selectEventsToSync[T](history: InteractionVariableHistory[T]): InteractionVariableHistory[T]

}

object SyncStrategy {

  object SYNC_EVERYTHING extends SyncStrategy {
    override def selectEventsToSync[T](history: InteractionVariableHistory[T]): InteractionVariableHistory[T] = history

    override val toString: String = "SYNC_EVERYTHING"
  }

  private case class DesiredRelevance(desired: List[UpdateImportance]) extends SyncStrategy {
    override def selectEventsToSync[T](history: InteractionVariableHistory[T]): InteractionVariableHistory[T] = history.map(_.filter(e => desired.contains(e.updateImportance)))

    override val toString: String = "SYNC_ONLY(" + desired.mkString(", ") + ")"
  }

  object SYNC_LAST extends SyncStrategy {
    override def selectEventsToSync[T](history: InteractionVariableHistory[T]): InteractionVariableHistory[T] = {
      history.map(_.filter(_.updateImportance != UpdateImportance.DEFAULT).maxByOption(_.timestamp).toSet)
    }

    override val toString: String = "SYNC_ONLY_LAST"
  }

  val SYNC_MAJOR: SyncStrategy = DesiredRelevance(List(UpdateImportance.MAJOR))
  val SYNC_MINOR: SyncStrategy = DesiredRelevance(List(UpdateImportance.MINOR, UpdateImportance.MAJOR))

}


