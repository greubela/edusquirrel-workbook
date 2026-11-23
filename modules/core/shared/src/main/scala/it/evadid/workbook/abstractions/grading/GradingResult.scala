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

    if (other.gradingStatus.shouldRetryExecution && !this.gradingStatus.shouldRetryExecution) this
    else if (!other.gradingStatus.shouldRetryExecution && this.gradingStatus.shouldRetryExecution) other
    else if (other.gradingTime.isAfter(this.gradingTime)) other
    else if (this.gradingTime.isAfter(other.gradingTime)) this
    else if (this.feedbackInformation.isEmpty && other.feedbackInformation.nonEmpty) other
    else if (this.feedbackInformation.nonEmpty && other.feedbackInformation.isEmpty) this
    else this.copy(feedbackInformation = Some(other.feedbackInformation.get.merge(this.feedbackInformation.get)))
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
    case Incorrect() => 4
    case PartiallyCorrect() => 3
    case Correct() => 2
    case NotFinished() => 0
    case NotRequested() => -1
  }


}
