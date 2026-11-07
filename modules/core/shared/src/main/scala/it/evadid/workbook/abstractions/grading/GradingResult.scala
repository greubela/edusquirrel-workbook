package it.evadid.workbook.abstractions.grading

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.abstractions.grading.GradingStatus.*
import upickle.default.*

import java.time.LocalDateTime

case class GradingResult[T, A]
(
  grader: WorkbookInteractionGrader[T, A],
  gradedState: T,
  gradingStatus: GradingStatus,
  feedbackInformation: Option[LanguageMap[HumanLanguage]] ,
  additionalData: Option[A] ,
  gradingTime: LocalDateTime,
) derives ReadWriter {



  def merge(other: GradingResult[T, A]): GradingResult[T, A] = {
    require(grader == other.grader && gradedState == other.gradedState, "Only results for the same grader and state can be merged.")
    val preferred =
      if gradingStatus.isErrorOrUnfinished != other.gradingStatus.isErrorOrUnfinished then
        if gradingStatus.isErrorOrUnfinished then other else this
      else if other.gradingTime.isAfter(gradingTime) then other
      else this
    val remaining = if preferred eq this then other else this
    val feedback = (remaining.feedbackInformation, preferred.feedbackInformation) match {
      case (Some(previous), Some(current)) => Some(LanguageMap.merge(previous, current))
      case _ => preferred.feedbackInformation.orElse(remaining.feedbackInformation)
    }
    preferred.copy(feedbackInformation = feedback, additionalData = preferred.additionalData.orElse(remaining.additionalData))
  }


}

object GradingResult {

  type GradingResultUntyped = GradingResult[?, ?]



  def apply[T, A](
                    grader: WorkbookInteractionGrader[T, A],
                    gradedState: T,
                    gradingStatus: GradingStatus,
                    feedbackInformation: Option[LanguageMap[HumanLanguage]] = None,
                    additionalData: Option[A] = None,
                  ): GradingResult[T, A] = GradingResult(grader, gradedState, gradingStatus, feedbackInformation, additionalData, LocalDateTime.now())

}


