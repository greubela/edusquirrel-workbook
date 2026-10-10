package it.evadid.workbook.elements.interactionElements.slideshow

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.FeedbackEntity.TestEntity
import it.evadid.workbook.abstractions.grading.GradingStatus.*
import it.evadid.workbook.abstractions.grading.{GradingStatus, GradingResult}
import upickle.default.*

case class SlideshowGradingResult(
                                   override val gradedState: SlideshowState,
                                 ) extends GradingResult[SlideshowState] derives ReadWriter {

  override val gradingEntity: FeedbackEntity = TestEntity()
  override val gradingStatus: GradingStatus = if (gradedState.unseenPanels.isEmpty) GradingStatus.CORRECT else GradingStatus.PARTIALLY_CORRECT

  override val feedbackInformation: Option[LanguageMap[AppLanguage.HumanLanguage]] = Some(gradingStatus.match {
    case CORRECT => LanguageMap.universalMap("All done!")
    case _ => LanguageMap.universalMap(s"Missing ${gradedState.unseenPanels} Panels!")
  })

}
