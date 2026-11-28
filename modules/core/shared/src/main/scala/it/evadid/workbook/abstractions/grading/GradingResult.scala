package it.evadid.workbook.abstractions.grading


import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId, WorkbookContent}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.grading.GradingStatus.*
import it.evadid.workbook.abstractions.{GraderReference, StateReference}
import upickle.default.*

import java.time.Instant

import it.evadid.core.util.io.serializer.DefaultSerializer.given
case class GradingResult[T, GA]
(
  grader: WorkbookInteractionGrader[T, GA],
  gradedState: T,
  gradingStatus: GradingStatus,
  feedbackInformation: Option[WorkbookContent],
  additionalData: Option[GA],
  gradingTime: Instant
  ) {


  /** This result as saved in a history, with its answer's `state` reference. */
  def record(state: StateReference): GradingRecord[GA] =
    GradingRecord(grader.reference, state, gradingStatus, feedbackInformation, additionalData, gradingTime)

  def merge(other: GradingResult[T, GA]): GradingResult[T, GA] = {
    require(grader == other.grader && gradedState == other.gradedState, "Only results for the same grader and state can be merged.")
    GradingResult.merged(this, other)(_.gradingStatus, _.gradingTime, _.feedbackInformation, (result, feedback) => result.copy(feedbackInformation = feedback))
  }
}

/** A grading result as saved in a history. It refers to its grader and answer instead of copying them; a reader
  * resolves it against the interaction's current graders and the answer it asks about.
  */
case class GradingRecord[GA](
  grader: GraderReference,
  state: StateReference,
  gradingStatus: GradingStatus,
  feedbackInformation: Option[WorkbookContent],
  additionalData: Option[GA],
  gradingTime: Instant
) derives ReadWriter {
  def resolve[T](grader: WorkbookInteractionGrader[T, GA], gradedState: T): GradingResult[T, GA] =
    GradingResult(grader, gradedState, gradingStatus, feedbackInformation, additionalData, gradingTime)

  def merge(other: GradingRecord[GA]): GradingRecord[GA] = {
    require(grader == other.grader && state == other.state, "Only results for the same grader and state can be merged.")
    GradingResult.merged(this, other)(_.gradingStatus, _.gradingTime, _.feedbackInformation, (record, feedback) => record.copy(feedbackInformation = feedback))
  }
}

object GradingResult {


  def apply[T, GA](grader: WorkbookInteractionGrader[T, GA],
                   gradedState: T,
                   gradingStatus: GradingStatus): GradingResult[T, GA] = {
    new GradingResult[T, GA](grader, gradedState, gradingStatus, None, None, Instant.now())
  }

  def apply[T, GA](grader: WorkbookInteractionGrader[T, GA],
                   gradedState: T,
                   gradingStatus: GradingStatus,
                   feedbackInformation: LanguageMap[HumanLanguage],
                   additionalData: Option[GA] ,
                  ): GradingResult[T, GA] = {
    new GradingResult(grader, gradedState, gradingStatus, Some(WorkbookContent(feedbackInformation)), additionalData, Instant.now())
  }



  def apply[T, GA](grader: WorkbookInteractionGrader[T, GA],
                   gradedState: T,
                   gradingStatus: GradingStatus,
                   feedbackInformation: LanguageMapContentId ,
                   additionalData: Option[GA] ,
                  ): GradingResult[T, GA] = {
   new GradingResult(grader, gradedState, gradingStatus, Some(WorkbookContent(feedbackInformation)), additionalData, Instant.now())
  }


  /** Two results of one grader for one answer: an assessment beats an error or pending state, then the later one
    * wins; the winner keeps the other's feedback in languages it lacks.
    */
  private[grading] def merged[R](first: R, second: R)(status: R => GradingStatus, time: R => Instant,
      feedback: R => Option[WorkbookContent], withFeedback: (R, Option[WorkbookContent]) => R): R = {
    val preferred =
      if statusPriority(status(second)) < 2 && statusPriority(status(first)) >= 2 then first
      else if statusPriority(status(second)) >= 2 && statusPriority(status(first)) < 2 then second
      else if time(second).isAfter(time(first)) then second
      else if time(first).isAfter(time(second)) then first
      else if statusPriority(status(second)) > statusPriority(status(first)) then second
      else first
    val remaining = if preferred == first then second else first
    withFeedback(preferred, (feedback(remaining), feedback(preferred)) match {
      case (Some(old: WorkbookContent.LanguageMapBasedContent), Some(current: WorkbookContent.LanguageMapBasedContent)) => Some(old.merge(current))
      case (_, Some(current)) => Some(current)
      case (previous, None) => previous
    })
  }

  private def statusPriority(status: GradingStatus): Int = status match {
    case TestError(_) => 1
    case Incorrect => 4
    case PartiallyCorrect => 3
    case Correct => 2
    case NotFinished => 0
    case NotRequested => -1
  }


}
