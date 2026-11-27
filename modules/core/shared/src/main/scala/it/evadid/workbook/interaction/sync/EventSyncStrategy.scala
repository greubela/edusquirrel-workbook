package it.evadid.workbook.interaction.sync

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.variable.events.{AnswerChangedEvent, InteractionEvent, InteractionEvents}

import java.time.LocalDateTime
import upickle.default.*

/** Applies uniformly to answers, grading and scaffolding, without removing related events implicitly. */
sealed trait EventSyncStrategy derives ReadWriter {
  def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]]): Set[InteractionEvent[F]]
}

object EventSyncStrategy {
  case object SYNC_EVERYTHING extends EventSyncStrategy {
    override def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]]) = events
  }

  case class KeepImportance(importance: Set[UpdateImportance]) extends EventSyncStrategy derives ReadWriter {
    override def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]]) =
      events.filter(event => importance(event.updateImportance))
  }

  /** Keep every event tied for the latest timestamp among the selected importance levels. */
  case class KeepLatest(importance: Set[UpdateImportance]) extends EventSyncStrategy derives ReadWriter {
    override def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]]) = {
      InteractionEvents.latestTies(events.filter(event => importance(event.updateImportance)))
    }
  }

  case class KeepLatestCount(count: Int) extends EventSyncStrategy derives ReadWriter {
    require(count > 0, "Event count must be positive")
    override def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]]) = {
      val boundary = events.toList.sortBy(_.timestamp).takeRight(count).headOption.map(_.timestamp)
      events.filter(event => boundary.exists(time => !event.timestamp.isBefore(time)))
    }
  }

  /** Preserve the latest event of each lifecycle kind as well as all events at the selected importance. */
  case class KeepLatestAndImportance(importance: Set[UpdateImportance]) extends EventSyncStrategy derives ReadWriter {
    override def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]]) = {
      val latest = events.groupBy(_.getClass.getName).values.flatMap(group => KeepLatest(UpdateImportance.values.toSet).selectEvents(group)).toSet
      events.filter(event => importance(event.updateImportance)) ++ latest
    }
  }

  /** Inclusive by default so events sharing a synchronization boundary are not lost. */
  case class Since(timestamp: LocalDateTime, includeBoundary: Boolean = true) extends EventSyncStrategy derives ReadWriter {
    override def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]]) =
      events.filter(event => event.timestamp.isAfter(timestamp) || (includeBoundary && event.timestamp == timestamp))
  }

  /** Send major answers and the latest answer that is not TEMPORARY, with all their feedback lifecycle events.
    * A TEMPORARY answer is not sent until a later MAJOR answer or an explicit sync commits it.
    */
  case object SYNC_MAJOR extends EventSyncStrategy {
    override def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]]): Set[InteractionEvent[F]] = {
      val settled = answers(events).filter(_.updateImportance != UpdateImportance.TEMPORARY)
      val states = (settled.filter(_.updateImportance == UpdateImportance.MAJOR) ++ latest(settled)).map(_.associatedState)
      events.filter(event => event.updateImportance == UpdateImportance.MAJOR || states(event.associatedState))
    }
  }

  /** Send the latest answer of any importance with all its feedback lifecycle events, e.g. for local storage. */
  case object SYNC_LAST extends EventSyncStrategy {
    override def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]]): Set[InteractionEvent[F]] = {
      val states = latest(answers(events)).map(_.associatedState)
      events.filter(event => states(event.associatedState))
    }
  }

  private def answers[F <: InteractionFormat](events: Set[InteractionEvent[F]]): Set[AnswerChangedEvent[F]] =
    events.collect { case event: AnswerChangedEvent[F] => event }

  /** Answers tied for the latest timestamp; a DEFAULT answer counts only if no other answer exists. */
  private def latest[F <: InteractionFormat](answers: Set[AnswerChangedEvent[F]]): Set[AnswerChangedEvent[F]] = {
    val nonDefault = answers.filter(_.updateImportance != UpdateImportance.DEFAULT)
    InteractionEvents.latestTies(if nonDefault.nonEmpty then nonDefault else answers)
  }

  val KEEP_MINOR: EventSyncStrategy = KeepImportance(Set(UpdateImportance.MINOR, UpdateImportance.MAJOR))
  val KEEP_LAST_FIVE: EventSyncStrategy = KeepLatestCount(5)
  val KEEP_LAST_AND_MAJOR: EventSyncStrategy = KeepLatestAndImportance(Set(UpdateImportance.MAJOR))
  val KEEP_LAST: EventSyncStrategy = KeepLatest(UpdateImportance.values.toSet)
  val KEEP_LAST_MAJOR: EventSyncStrategy = KeepLatest(Set(UpdateImportance.MAJOR))
}
