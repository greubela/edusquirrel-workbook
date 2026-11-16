package it.evadid.workbook.interaction.sync

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.interaction.variable.events.InteractionEvent
import java.time.LocalDateTime
import upickle.default.*

/** Applies uniformly to answers, grading and scaffolding, without removing related events implicitly. */
sealed trait EventSyncStrategy derives ReadWriter {
  def selectEvents[T, GA, SA](events: Set[InteractionEvent[T, GA, SA]]): Set[InteractionEvent[T, GA, SA]]
}

object EventSyncStrategy {
  case object KEEP_ALL extends EventSyncStrategy {
    override def selectEvents[T, GA, SA](events: Set[InteractionEvent[T, GA, SA]]) = events
  }

  case class KeepImportance(importance: Set[UpdateImportance]) extends EventSyncStrategy derives ReadWriter {
    override def selectEvents[T, GA, SA](events: Set[InteractionEvent[T, GA, SA]]) =
      events.filter(event => importance(event.updateImportance))
  }

  /** Keep every event tied for the latest timestamp among the selected importance levels. */
  case class KeepLatest(importance: Set[UpdateImportance]) extends EventSyncStrategy derives ReadWriter {
    override def selectEvents[T, GA, SA](events: Set[InteractionEvent[T, GA, SA]]) = {
      val selected = events.filter(event => importance(event.updateImportance))
      val latest = selected.map(_.timestamp).maxOption
      selected.filter(event => latest.contains(event.timestamp))
    }
  }

  /** Inclusive by default so events sharing a synchronization boundary are not lost. */
  case class Since(timestamp: LocalDateTime, includeBoundary: Boolean = true) extends EventSyncStrategy derives ReadWriter {
    override def selectEvents[T, GA, SA](events: Set[InteractionEvent[T, GA, SA]]) =
      events.filter(event => event.timestamp.isAfter(timestamp) || (includeBoundary && event.timestamp == timestamp))
  }

  val KEEP_LAST: EventSyncStrategy = KeepLatest(UpdateImportance.values.toSet)
  val KEEP_LAST_MAJOR: EventSyncStrategy = KeepLatest(Set(UpdateImportance.MAJOR))
}
