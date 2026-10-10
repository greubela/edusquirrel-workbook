package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.FeedbackEntity.TestEntity
import it.evadid.workbook.abstractions.grading.{GradingStatus, GradingResult}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

case class RecreateShapeGradingResult
(
  override val gradedState: ProgrammingState,
  override val gradingStatus: GradingStatus,
  override val feedbackInformation: Option[LanguageMap[AppLanguage.HumanLanguage]]


) extends GradingResult[ProgrammingState] {

  override val gradingEntity: FeedbackEntity = TestEntity()

}
