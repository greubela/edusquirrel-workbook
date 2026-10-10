package it.evadid.workbook.elements.interactionElements.slideshow

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.FeedbackEntity.TestEntity
import it.evadid.workbook.abstractions.grading.GradingGrade.*
import it.evadid.workbook.abstractions.grading.{GradingGrade, GradingResult}
import upickle.default.*

case class SlideshowGradingResult(
                                   override val gradedState: SlideshowState,
                                 ) extends GradingResult[SlideshowState] derives ReadWriter {

  override val gradingEntity: FeedbackEntity = TestEntity()
  override val gradingGrade: GradingGrade = if (gradedState.unseenPanels.isEmpty) GradingGrade.CORRECT else GradingGrade.PARTIALLY_CORRECT

  override val feedbackInformation: LanguageMap[AppLanguage.HumanLanguage] = gradingGrade.match {
    case CORRECT => LanguageMap.universalMap("All done!")
    case _ => LanguageMap.universalMap(s"Missing ${gradedState.unseenPanels} Panels!")
  }

}
