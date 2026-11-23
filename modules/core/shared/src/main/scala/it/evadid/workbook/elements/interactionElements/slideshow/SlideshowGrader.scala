package it.evadid.workbook.elements.interactionElements.slideshow

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap, WorkbookContent}
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.FeedbackEntity.TestEntity
import it.evadid.workbook.abstractions.grading.GraderRunningBehavior.RUN_ON_AT_LEAST_MINOR
import it.evadid.workbook.abstractions.grading.GradingImportance.{OPTIONAL_TEST, REQUIRED_TEST}
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.abstractions.grading.GradingStatus.Correct
import it.evadid.workbook.meta.ExecutionContextInformation
import upickle.default.*

import java.time.LocalDateTime
import scala.concurrent.Future

case class SlideshowGrader(isRequired: Boolean) extends WorkbookInteractionGrader[SlideshowState, Unit] derives ReadWriter {

  override def runningBehavior: GraderRunningBehavior = RUN_ON_AT_LEAST_MINOR

  override def importance: GradingImportance = if (isRequired) REQUIRED_TEST else OPTIONAL_TEST

  override def gradingEntity: FeedbackEntity = TestEntity()

  override protected def handleGrading(state: SlideshowState, contextInformation: ExecutionContextInformation): Future[GradingResult[SlideshowState, Unit]] = Future {

    val gradingStatus: GradingStatus = if (state.unseenPanels.isEmpty) GradingStatus.Correct()
      else if state.seenPanels.isEmpty then GradingStatus.Incorrect() else GradingStatus.PartiallyCorrect()
    val feedbackInformation: LanguageMap[HumanLanguage] = gradingStatus.match {
      case Correct() => LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "All slides viewed.",
        AppLanguage.German -> "Alle Folien angesehen."))
      case _ => LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> s"${state.unseenPanels.size} slides remain.",
        AppLanguage.German -> s"${state.unseenPanels.size} Folien fehlen noch."))
    }
    val res: GradingResult[SlideshowState, Unit] = GradingResult[SlideshowState, Unit](this, state, gradingStatus, Some(WorkbookContent(feedbackInformation)), None, LocalDateTime.now())
    res
  }
}
