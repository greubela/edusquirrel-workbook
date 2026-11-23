package it.evadid.workbook.elements.interactionElements.choice

import it.evadid.core.datastructures.language.{LanguageMapContentId, WorkbookContent}
import it.evadid.workbook.abstractions.FeedbackEntity.TestEntity
import it.evadid.workbook.abstractions.InteractionFormat.*
import it.evadid.workbook.abstractions.grading.GradingStatus.TestError
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.abstractions.{FeedbackEntity, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.choice.WorkbookContentChoiceInteraction.*
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.meta.ExecutionContextInformation
import upickle.default.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given

import java.time.LocalDateTime
import scala.concurrent.Future

case class WorkbookContentChoiceInteraction(
                                             elementId: String,
                                             config: ChoiceConfig,
                                             options: Seq[ChoiceOptionWorkbookContent]
                                           ) extends WorkbookInteractionElement[ChoiceInteractionFormat.type] {

  require(options.nonEmpty && options.distinct == options, "Provide distinct and non empty choices")
  require(options.forall(_.gradingForChoice.isDefined) || options.forall(_.gradingForChoice.isEmpty), "Either all or none of the options must have gradings!")

  override lazy val childrenOfThisElement = Nil
  override val defaultValue: ChoiceState = ChoiceState(List())

  override val associatedFactory = WorkbookContentChoiceInteraction.factory

  lazy val graderOption: Option[WorkbookInteractionGrader[ChoiceState, Unit]] = if (options.exists(_.gradingForChoice.isEmpty)) None else {
    val res = new WorkbookInteractionGrader[ChoiceState, Unit]() {

      override protected def handleGrading(state: ChoiceState, contextInformation: ExecutionContextInformation): Future[GradingResult[ChoiceState, Unit]] = Future {
        state.lastEvent.match {
          case None => GradingResult(this, state, TestError("Nothing selected yet!"))
          case Some(lastEvent) => lastEvent.selected.gradingForChoice.match {
            case None => GradingResult(this, state, TestError("No grading available for this ChoiceInteraction!"))
            case Some(grading) => GradingResult(this, state, grading.answerCorrectness)
          }
        }

      }

      override def importance: GradingImportance = GradingImportance.REQUIRED_TEST

      override def runningBehavior: GraderRunningBehavior = config.gradingBehavior

      override def gradingEntity: FeedbackEntity = TestEntity()
    }
    Some(res)
  }


  override val gradingElements: Seq[WorkbookInteractionGrader[InteractionStateType, GradingInfoType]] = graderOption.toList

}

object WorkbookContentChoiceInteraction {

  case class ChoiceConfig(instruction: LanguageMapContentId, allowMultipleSelected: Option[Boolean], gradingBehavior: GraderRunningBehavior) derives ReadWriter

  case class GradingForChoice(answerCorrectness: GradingStatus, feedbackIfChosen: LanguageMapContentId) derives ReadWriter

  case class ChoiceOptionWorkbookContent(content: WorkbookContent, gradingForChoice: Option[GradingForChoice]) derives ReadWriter

  case class ChoiceSelectedEvent(timestamp: LocalDateTime, selected: ChoiceOptionWorkbookContent) derives ReadWriter

  case class ChoiceState(selectionEvents: Seq[ChoiceSelectedEvent]) derives ReadWriter {

    lazy val lastEvent: Option[ChoiceSelectedEvent] = selectionEvents.maxByOption(_.timestamp)

    lazy val currentlySelected: Option[ChoiceOptionWorkbookContent] = lastEvent.map(_.selected)

    lazy val isAnswered: Boolean = lastEvent.nonEmpty

  }

  val factory: WorkbookElementFactory.SimpleWorkbookElementFactory[WorkbookContentChoiceInteraction] = new WorkbookElementFactory.SimpleWorkbookElementFactory[WorkbookContentChoiceInteraction] {
    override protected val constructorFieldOrder = List("elementId", "prompt", "options", "allowMultiple", "expected")

    override def finishSerialization(base: WorkbookElementSerializable, e: WorkbookContentChoiceInteraction): WorkbookElementSerializable =
      base
        .withElementAddedAs("config", e.config)
        .withElementAddedAs("options", e.options)

    override def finishDeserialization(e: WorkbookElementSerializable): WorkbookContentChoiceInteraction =
      WorkbookContentChoiceInteraction(e.elementId, e.getElementAs[ChoiceConfig]("config"), e.getElementAs[Seq[ChoiceOptionWorkbookContent]]("options"))
  }


}
