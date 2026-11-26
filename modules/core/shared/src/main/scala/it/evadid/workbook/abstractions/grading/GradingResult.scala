package it.evadid.workbook.abstractions.grading

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId, WorkbookContent}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.grading.GradingStatus.*
import upickle.default.*

import java.time.LocalDateTime

import it.evadid.core.util.io.serializer.DefaultSerializer.given
case class GradingResult[T, GA]
(
  grader: WorkbookInteractionGrader[T, GA],
  gradedState: T,
  gradingStatus: GradingStatus,
  feedbackInformation: Option[WorkbookContent],
  additionalData: Option[GA],
  gradingTime: LocalDateTime
) derives ReadWriter {


  def merge(other: GradingResult[T, GA]): GradingResult[T, GA] = {
    require(grader == other.grader && gradedState == other.gradedState, "Only results for the same grader and state can be merged.")

    val preferred =
      if GradingResult.statusPriority(other.gradingStatus) < 2 && GradingResult.statusPriority(gradingStatus) >= 2 then this
      else if GradingResult.statusPriority(other.gradingStatus) >= 2 && GradingResult.statusPriority(gradingStatus) < 2 then other
      else if other.gradingTime.isAfter(gradingTime) then other
      else if gradingTime.isAfter(other.gradingTime) then this
      else if GradingResult.statusPriority(other.gradingStatus) > GradingResult.statusPriority(gradingStatus) then other
      else this
    val remaining = if preferred eq this then other else this
    val feedback = (remaining.feedbackInformation, preferred.feedbackInformation) match {
      case (Some(old: WorkbookContent.LanguageMapBasedContent), Some(current: WorkbookContent.LanguageMapBasedContent)) => Some(old.merge(current))
      case (_, Some(current)) => Some(current)
      case (previous, None) => previous
    }
    preferred.copy(feedbackInformation = feedback)
  }


}

object GradingResult {


  def apply[T, GA](grader: WorkbookInteractionGrader[T, GA],
                   gradedState: T,
                   gradingStatus: GradingStatus): GradingResult[T, GA] = {
    new GradingResult[T, GA](grader, gradedState, gradingStatus, None, None, LocalDateTime.now())
  }

  def apply[T, GA](grader: WorkbookInteractionGrader[T, GA],
                   gradedState: T,
                   gradingStatus: GradingStatus,
                   feedbackInformation: LanguageMap[HumanLanguage],
                   additionalData: Option[GA] ,
                  ): GradingResult[T, GA] = {
    new GradingResult(grader, gradedState, gradingStatus, Some(WorkbookContent(feedbackInformation)), additionalData, LocalDateTime.now())
  }



  def apply[T, GA](grader: WorkbookInteractionGrader[T, GA],
                   gradedState: T,
                   gradingStatus: GradingStatus,
                   feedbackInformation: LanguageMapContentId ,
                   additionalData: Option[GA] ,
                  ): GradingResult[T, GA] = {
   new GradingResult(grader, gradedState, gradingStatus, Some(WorkbookContent(feedbackInformation)), additionalData, LocalDateTime.now())
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
