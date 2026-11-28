package it.evadid.workbook.sync

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.{InteractionFormat, StateReference}
import it.evadid.workbook.history.events.{AnswerChangedEvent, InteractionEvent, InteractionEvents}

import java.time.Instant
import upickle.default.*

/** Applies uniformly to answers, grading and scaffolding, without removing related events implicitly. */
/** `stateOf` names the answer an event belongs to, as its history derives it. */
sealed trait EventSyncStrategy derives ReadWriter {
  def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]], stateOf: InteractionEvent[F] => StateReference): Set[InteractionEvent[F]]
}

object EventSyncStrategy {
  @upickle.implicits.key("SYNC_EVERYTHING")
  case object SYNC_EVERYTHING extends EventSyncStrategy {
    override def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]], stateOf: InteractionEvent[F] => StateReference) = events
  }

  @upickle.implicits.key("KeepImportance")
  case class KeepImportance(importance: Set[UpdateImportance]) extends EventSyncStrategy derives ReadWriter {
    override def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]], stateOf: InteractionEvent[F] => StateReference) =
      events.filter(event => importance(event.updateImportance))
  }

  /** Keep every event tied for the latest timestamp among the selected importance levels. */
  @upickle.implicits.key("KeepLatest")
  case class KeepLatest(importance: Set[UpdateImportance]) extends EventSyncStrategy derives ReadWriter {
    override def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]], stateOf: InteractionEvent[F] => StateReference) = {
      InteractionEvents.latestTies(events.filter(event => importance(event.updateImportance)))
    }
  }

  @upickle.implicits.key("KeepLatestCount")
  case class KeepLatestCount(count: Int) extends EventSyncStrategy derives ReadWriter {
    require(count > 0, "Event count must be positive")
    override def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]], stateOf: InteractionEvent[F] => StateReference) = {
      val boundary = events.toList.sortBy(_.timestamp).takeRight(count).headOption.map(_.timestamp)
      events.filter(event => boundary.exists(time => !event.timestamp.isBefore(time)))
    }
  }

  /** Preserve the latest event of each lifecycle kind as well as all events at the selected importance. */
  @upickle.implicits.key("KeepLatestAndImportance")
  case class KeepLatestAndImportance(importance: Set[UpdateImportance]) extends EventSyncStrategy derives ReadWriter {
    override def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]], stateOf: InteractionEvent[F] => StateReference) = {
      val latest = events.groupBy(_.getClass.getName).values.flatMap(group => KeepLatest(UpdateImportance.values.toSet).selectEvents(group, stateOf)).toSet
      events.filter(event => importance(event.updateImportance)) ++ latest
    }
  }

  /** Inclusive by default so events sharing a synchronization boundary are not lost. */
  @upickle.implicits.key("Since")
  case class Since(timestamp: Instant, includeBoundary: Boolean = true) extends EventSyncStrategy derives ReadWriter {
    override def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]], stateOf: InteractionEvent[F] => StateReference) =
      events.filter(event => event.timestamp.isAfter(timestamp) || (includeBoundary && event.timestamp == timestamp))
  }

  /** Send major answers and the latest answer that is not TEMPORARY, with all their feedback lifecycle events.
    * A TEMPORARY answer is not sent until a later MAJOR answer or an explicit sync commits it.
    */
  @upickle.implicits.key("SYNC_MAJOR")
  case object SYNC_MAJOR extends EventSyncStrategy {
    override def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]], stateOf: InteractionEvent[F] => StateReference): Set[InteractionEvent[F]] = {
      val settled = answers(events).filter(_.updateImportance != UpdateImportance.TEMPORARY)
      val states = (settled.filter(_.updateImportance == UpdateImportance.MAJOR) ++ latest(settled)).map(stateOf)
      events.filter(event => event.updateImportance == UpdateImportance.MAJOR || states(stateOf(event)))
    }
  }

  /** Send the latest answer of any importance with all its feedback lifecycle events, e.g. for local storage. */
  @upickle.implicits.key("SYNC_LAST")
  case object SYNC_LAST extends EventSyncStrategy {
    override def selectEvents[F <: InteractionFormat](events: Set[InteractionEvent[F]], stateOf: InteractionEvent[F] => StateReference): Set[InteractionEvent[F]] = {
      val states = latest(answers(events)).map(stateOf)
      events.filter(event => states(stateOf(event)))
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
