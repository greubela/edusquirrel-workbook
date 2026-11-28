package it.evadid.workbook.interactions.choice

import it.evadid.core.util.UtcTime

import it.evadid.core.datastructures.language.{LanguageMapContentId, WorkbookContent}
import it.evadid.workbook.abstractions.InteractionFormat.ChoiceInteractionFormat
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.interactions.choice.WorkbookContentChoiceInteraction.*
import it.evadid.workbook.serialization.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import java.time.LocalDateTime
import upickle.default.*

case class WorkbookContentChoiceInteraction(elementId: String, config: ChoiceConfig, options: Seq[ChoiceOptionWorkbookContent])
    extends WorkbookInteractionElement[ChoiceInteractionFormat.type] {
  require(options.nonEmpty && options.map(_.content).distinct.size == options.size, "Provide distinct nonempty choices")
  require(options.forall(_.gradingForChoice.isDefined) || options.forall(_.gradingForChoice.isEmpty), "Either all or none of the options must have gradings")
  override lazy val childrenOfThisElement = Nil
  override val defaultValue = ChoiceState(Seq.empty)
  override val associatedFactory = WorkbookContentChoiceInteraction.factory
  override val gradingElements = Option.when(options.forall(_.gradingForChoice.nonEmpty))(
    ChoiceGrader(options, config.allowMultipleSelected.contains(true), config.gradingBehavior)).toSeq

  override def validateState(state: ChoiceState): Unit = {
    require(state.selectionEvents.forall(event => options.contains(event.selected)), "Stored choice does not belong to this exercise")
    require(config.allowMultipleSelected.contains(true) || state.selectedOptions.size <= 1, "Only one choice may be selected")
  }

  /** Indices of the selected options that are graded incorrect. */
  override def wrongParts(state: ChoiceState): Set[Int] = ChoiceGrader.wrongSelections(options, state)

  def select(state: ChoiceState, index: Int, selected: Boolean, timestamp: LocalDateTime = UtcTime.now()): ChoiceState = {
    validateState(state)
    require(options.indices.contains(index), "Choice index is outside the exercise")
    val option = options(index)
    if state.selectedOptions.contains(option) == selected then state
    else {
      val removed = if selected && !config.allowMultipleSelected.contains(true) then state.selectedOptions else Seq.empty
      state.copy(selectionEvents = state.selectionEvents ++ removed.map(ChoiceSelectedEvent(timestamp, _, false)) :+
        ChoiceSelectedEvent(timestamp, option, selected))
    }
  }
}

object WorkbookContentChoiceInteraction {
  case class ChoiceConfig(instruction: LanguageMapContentId, allowMultipleSelected: Option[Boolean], gradingBehavior: GraderRunningBehavior) derives ReadWriter
  case class GradingForChoice(answerCorrectness: GradingStatus, feedbackIfChosen: LanguageMapContentId) derives ReadWriter
  case class ChoiceOptionWorkbookContent(content: WorkbookContent, gradingForChoice: Option[GradingForChoice]) derives ReadWriter
  case class ChoiceSelectedEvent(timestamp: LocalDateTime, selected: ChoiceOptionWorkbookContent, isSelected: Boolean = true) derives ReadWriter
  case class ChoiceState(selectionEvents: Seq[ChoiceSelectedEvent]) derives ReadWriter {
    private lazy val ordered = selectionEvents.zipWithIndex.sortBy((event, index) => (event.timestamp, index)).map(_._1)
    lazy val lastEvent: Option[ChoiceSelectedEvent] = ordered.lastOption
    lazy val selectedOptions: Seq[ChoiceOptionWorkbookContent] = ordered.foldLeft(Vector.empty[ChoiceOptionWorkbookContent]) { (selected, event) =>
      val remaining = selected.filterNot(_ == event.selected)
      if event.isSelected then remaining :+ event.selected else remaining
    }
    lazy val currentlySelected: Option[ChoiceOptionWorkbookContent] = selectedOptions.lastOption
    lazy val isAnswered: Boolean = selectedOptions.nonEmpty
  }

  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[WorkbookContentChoiceInteraction] {
    override protected val constructorFieldOrder = List("elementId", "config", "options")
    override def finishSerialization(base: WorkbookElementSerializable, e: WorkbookContentChoiceInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("config", e.config).withElementAddedAs("options", e.options)
    override def finishDeserialization(e: WorkbookElementSerializable): WorkbookContentChoiceInteraction =
      WorkbookContentChoiceInteraction(e.elementId, e.getElementAs[ChoiceConfig]("config"), e.getElementAs[Seq[ChoiceOptionWorkbookContent]]("options"))
  }
}
