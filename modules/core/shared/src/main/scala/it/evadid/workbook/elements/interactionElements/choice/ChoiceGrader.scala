package it.evadid.workbook.elements.interactionElements.choice

import it.evadid.core.datastructures.language.WorkbookContent
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.elements.interactionElements.choice.WorkbookContentChoiceInteraction.*
import it.evadid.workbook.meta.ExecutionContextInformation
import java.time.LocalDateTime
import scala.concurrent.Future
import upickle.default.ReadWriter

case class ChoiceGrader(options: Seq[ChoiceOptionWorkbookContent], allowMultiple: Boolean,
    runningBehavior: GraderRunningBehavior) extends WorkbookInteractionGrader[ChoiceState, Unit] derives ReadWriter {
  override val importance = GradingImportance.REQUIRED_TEST
  override val gradingEntity = FeedbackEntity.TestEntity()
  override protected def handleGrading(state: ChoiceState, context: ExecutionContextInformation): Future[GradingResult[ChoiceState, Unit]] = Future {
    require(state.selectionEvents.forall(event => options.contains(event.selected)), "Choice does not belong to this exercise")
    val selected = state.selectedOptions
    require(allowMultiple || selected.size <= 1, "Only one choice may be selected")
    val grades = selected.flatMap(_.gradingForChoice)
    val expected = options.filter(_.gradingForChoice.exists(_.answerCorrectness.isCorrect)).toSet
    val status = if selected.isEmpty then GradingStatus.Incorrect()
      else if !allowMultiple then grades.head.answerCorrectness
      else if grades.exists(_.answerCorrectness == GradingStatus.Incorrect()) then GradingStatus.Incorrect()
      else if selected.toSet == expected then GradingStatus.Correct()
      else GradingStatus.PartiallyCorrect()
    val feedback = if !allowMultiple then grades.headOption.map(g => WorkbookContent(g.feedbackIfChosen)) else None
    GradingResult(this, state, status, feedback, None, LocalDateTime.now())
  }(using context.futureExecutionContext)
}
