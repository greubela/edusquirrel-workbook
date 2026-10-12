package it.evadid.workbook.elements.interactionElements.slideshow

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.FeedbackEntity.TestEntity
import it.evadid.workbook.abstractions.grading.GraderRunningBehavior.RUN_ALWAYS
import it.evadid.workbook.abstractions.grading.GradingImportance.{OPTIONAL_TEST, REQUIRED_TEST}
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.abstractions.grading.GradingStatus.Correct
import upickle.default.*

import java.time.LocalDateTime
import scala.concurrent.Future

case class SlideshowGrader(isRequired: Boolean) extends WorkbookInteractionGrader[SlideshowState, Nothing] derives ReadWriter {

  override def gradeState(state: SlideshowState): Future[GradingResult[SlideshowState, Nothing]] = Future {

    val gradingStatus: GradingStatus = if (state.unseenPanels.isEmpty) GradingStatus.Correct() else GradingStatus.PartiallyCorrect()
    val feedbackInformation: LanguageMap[AppLanguage.HumanLanguage] = gradingStatus.match {
      case Correct() => LanguageMap.universalMap("All done!")
      case _ => LanguageMap.universalMap(s"Missing ${state.unseenPanels} Panels!")
    }

    GradingResult(this, state, gradingStatus, Some(feedbackInformation))
  }

  override def runningBehavior: GraderRunningBehavior = RUN_ALWAYS

  override def importance: GradingImportance = if (isRequired) REQUIRED_TEST else OPTIONAL_TEST

  override def gradingEntity: FeedbackEntity = TestEntity()
}
