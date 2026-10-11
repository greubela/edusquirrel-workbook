package it.evadid.workbook.interaction.sync

import upickle.default.*
import it.evadid.workbook.interaction.sync.UpdateImportance.{DEFAULT, MAJOR, MINOR}
import it.evadid.workbook.interaction.variable.{InteractionVariableHistory, InteractionVariableState}


sealed trait SyncStrategy derives ReadWriter {

  def selectEventsToSync[T](events: Set[InteractionVariableState[T]]): Set[InteractionVariableState[T]]

}

object SyncStrategy {

  case object SYNC_EVERYTHING extends SyncStrategy {
    override def selectEventsToSync[T](history: Set[InteractionVariableState[T]]): Set[InteractionVariableState[T]] = history

    override val toString: String = "SYNC_EVERYTHING"
  }

  private case class DesiredRelevance(desired: List[UpdateImportance]) extends SyncStrategy {
    override def selectEventsToSync[T](history: Set[InteractionVariableState[T]]): Set[InteractionVariableState[T]] = history.filter(e => desired.contains(e.updateImportance))

    override val toString: String = "SYNC_ONLY(" + desired.mkString(", ") + ")"
  }

  case object SYNC_LAST_AND_MAJOR extends SyncStrategy {
    override def selectEventsToSync[T](history: Set[InteractionVariableState[T]]): Set[InteractionVariableState[T]] = {
      val lastEvent = history.filter(_.updateImportance != DEFAULT).maxByOption(_.timestamp)
      val majorEvents = history.filter(_.updateImportance == MAJOR)
      (majorEvents ++ lastEvent)
    }

    override val toString: String = "SYNC_LAST_AND_MAJOR"
  }

  case object SYNC_LAST extends SyncStrategy {
    override def selectEventsToSync[T](history: Set[InteractionVariableState[T]]): Set[InteractionVariableState[T]] = {
      history.filter(_.updateImportance != UpdateImportance.DEFAULT).maxByOption(_.timestamp).toSet
    }

    override val toString: String = "SYNC_ONLY_LAST"
  }

  val SYNC_MAJOR: SyncStrategy = DesiredRelevance(List(UpdateImportance.MAJOR))
  val SYNC_MINOR: SyncStrategy = DesiredRelevance(List(UpdateImportance.MINOR, UpdateImportance.MAJOR))

}


