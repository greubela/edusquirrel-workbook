package it.evadid.workbook.interaction.sync

import upickle.default.*

import it.evadid.workbook.interaction.sync.UpdateImportance.{DEFAULT, MAJOR, MINOR}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory


sealed trait SyncStrategy derives ReadWriter {

  def selectEventsToSync[T](history: InteractionVariableHistory[T]): InteractionVariableHistory[T]

}

object SyncStrategy {

  case object SYNC_EVERYTHING extends SyncStrategy {
    override def selectEventsToSync[T](history: InteractionVariableHistory[T]): InteractionVariableHistory[T] = history

    override val toString: String = "SYNC_EVERYTHING"
  }

  private case class DesiredRelevance(desired: List[UpdateImportance]) extends SyncStrategy derives upickle.default.ReadWriter {
    override def selectEventsToSync[T](history: InteractionVariableHistory[T]): InteractionVariableHistory[T] = history.map(_.filter(e => desired.contains(e.updateImportance)))

    override val toString: String = "SYNC_ONLY(" + desired.mkString(", ") + ")"
  }

  case object SYNC_LAST_AND_MAJOR extends SyncStrategy {
    override def selectEventsToSync[T](history: InteractionVariableHistory[T]): InteractionVariableHistory[T] = {
      val lastEvent = history.events.filter(_.updateImportance != DEFAULT).maxByOption(_.timestamp)
      val majorEvents = history.events.filter(_.updateImportance == MAJOR)
      InteractionVariableHistory(majorEvents ++ lastEvent)
    }

    override val toString: String = "SYNC_LAST_AND_MAJOR"
  }

  case object SYNC_LAST extends SyncStrategy {
    override def selectEventsToSync[T](history: InteractionVariableHistory[T]): InteractionVariableHistory[T] = {
      history.map(_.filter(_.updateImportance != UpdateImportance.DEFAULT).maxByOption(_.timestamp).toSet)
    }

    override val toString: String = "SYNC_ONLY_LAST"
  }

  val SYNC_MAJOR: SyncStrategy = DesiredRelevance(List(UpdateImportance.MAJOR))
  val SYNC_MINOR: SyncStrategy = DesiredRelevance(List(UpdateImportance.MINOR, UpdateImportance.MAJOR))

}


