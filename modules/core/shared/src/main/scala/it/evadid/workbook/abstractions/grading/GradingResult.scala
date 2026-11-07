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
    if (this == other || this.gradedState != other.gradedState) this
    else if (this.gradingStatus.isErrorOrUnfinished && !other.gradingStatus.isErrorOrUnfinished)
      other
    else if (!this.gradingStatus.isErrorOrUnfinished && other.gradingStatus.isErrorOrUnfinished)
      this
    else {
      val combinedData = if (additionalData.isEmpty) other.additionalData else additionalData
      val combinedFeedback: LanguageMap[HumanLanguage] =
        if (feedbackInformation.nonEmpty && other.feedbackInformation.nonEmpty) {
          LanguageMap.merge(feedbackInformation.get, other.feedbackInformation.get)
        } else if (feedbackInformation.nonEmpty) {
          feedbackInformation.get
        } else {
          other.feedbackInformation.get
        }
      val combinedTime = if (gradingTime.isAfter(other.gradingTime)) gradingTime else other.gradingTime

      GradingResult(grader, gradedState, gradingStatus, Some(combinedFeedback), combinedData, gradingTime)

    }

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


