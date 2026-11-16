package it.evadid.workbook.abstractions.grading

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.vm.conversion.javaexpression.checked.JavaTurtleResolution.If
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.abstractions.grading.GradingStatus.*
import upickle.default.*

import java.time.LocalDateTime

case class GradingResult[F <: InteractionFormat]
(
  grader: WorkbookInteractionGrader[F],
  gradedState: F#InteractionStateType,
  gradingStatus: GradingStatus,
  feedbackInformation: Option[LanguageMap[HumanLanguage]],
  additionalData: Option[F#GradingInfoType],
  gradingTime: LocalDateTime
) derives ReadWriter {

  def merge(other: GradingResult[F]): GradingResult[F] = {
    require(grader == other.grader && gradedState == other.gradedState, "Only results for the same grader and state can be merged.")
    val preferred =
      if (gradingStatus == NotRequested() || gradingStatus == NotFinished()) && !other.gradingStatus.shouldRetryExecution then other
      else if (other.gradingStatus == NotRequested() || other.gradingStatus == NotFinished()) && !gradingStatus.shouldRetryExecution then this
      else if other.gradingTime.isAfter(gradingTime) then other
      else if gradingTime == other.gradingTime && GradingResult.statusPriority(other.gradingStatus) > GradingResult.statusPriority(gradingStatus) then other
      else this
    val remaining = if preferred eq this then other else this
    val feedback = (remaining.feedbackInformation, preferred.feedbackInformation) match {
      case (Some(previous), Some(current)) => if it.evadid.core.datastructures.language.AppLanguage.humanLanguages.forall(language =>
          previous.tryGetInLanguage(language).isEmpty || current.tryGetInLanguage(language).nonEmpty) then Some(current)
        else Some(LanguageMap.mapBasedLanguageMap(it.evadid.core.datastructures.language.AppLanguage.humanLanguages.flatMap { language =>
          current.tryGetInLanguage(language).orElse(previous.tryGetInLanguage(language)).map(language -> _)
        }.toMap))
      case _ => preferred.feedbackInformation.orElse(remaining.feedbackInformation)
    }
    preferred.copy(feedbackInformation = feedback)
  }


}

object GradingResult {


  def apply[F <: InteractionFormat](grader: WorkbookInteractionGrader[F],
               gradedState: F#InteractionStateType,
               gradingStatus: GradingStatus,
               feedbackInformation: Option[LanguageMap[HumanLanguage]] = None,
               additionalData: Option[F#GradingInfoType] = None,
                 ): GradingResult[F] = GradingResult(grader, gradedState, gradingStatus, feedbackInformation, additionalData, LocalDateTime.now())

  private def statusPriority(status: GradingStatus): Int = status match {
    case TestError(_) => 1
    case Incorrect() => 4
    case PartiallyCorrect() => 3
    case Correct() => 2
    case NotFinished() => 0
    case NotRequested() => -1
  }


}
