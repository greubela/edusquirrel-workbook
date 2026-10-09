package it.evadid.workbook.elements.interactionElements.choice

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

case class ChoiceAnswer(selected: List[Int] = Nil) derives ReadWriter {
  require(selected.forall(_ >= 0) && selected.distinct == selected, "Selections must be unique nonnegative indices")
}

/** expected=None is an opinion/reflection question, with no invented correct answer. */
case class ChoiceInteraction(elementId: String, prompt: LanguageMapContentId, options: List[LanguageMapContentId],
    allowMultiple: Boolean = false, expected: Option[List[Int]] = None) extends WorkbookInteractionElement[ChoiceAnswer] {
  require(options.nonEmpty && options.distinct == options, "Provide distinct choices")
  require(expected.forall(xs => xs.nonEmpty && xs.distinct == xs && xs.forall(i => i >= 0 && i < options.size)
    && (allowMultiple || xs.size == 1)), "Invalid expected selection")
  override lazy val childrenOfThisElement = Nil
  override val defaultValue = ChoiceAnswer()
  override val serializerInteractionContent = Serializer.fromUpickleJson(summon[ReadWriter[ChoiceAnswer]])
  override val associatedFactory = ChoiceInteraction.factory
  def isValid(answer: ChoiceAnswer): Boolean = answer.selected.forall(_ < options.size) && (allowMultiple || answer.selected.size <= 1)
  def select(answer: ChoiceAnswer, index: Int, checked: Boolean): ChoiceAnswer = {
    require(index >= 0 && index < options.size, "Choice index is outside the options")
    require(isValid(answer), "Invalid stored selection")
    if !allowMultiple then ChoiceAnswer(if checked then List(index) else answer.selected.filterNot(_ == index))
    else ChoiceAnswer((if checked then (answer.selected :+ index).distinct else answer.selected.filterNot(_ == index)).sorted)
  }
  def isAnswered(answer: ChoiceAnswer): Boolean = isValid(answer) && answer.selected.nonEmpty
  def isCorrect(answer: ChoiceAnswer): Option[Boolean] = expected.map(xs => isAnswered(answer) && xs.toSet == answer.selected.toSet)
}
object ChoiceInteraction {
  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[ChoiceInteraction] {
    override protected val constructorFieldOrder = List("elementId", "prompt", "options", "allowMultiple", "expected")
    override def finishSerialization(base: WorkbookElementSerializable, e: ChoiceInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("prompt", e.prompt).withElementAddedAs("options", e.options)
        .withElementAddedAs("allowMultiple", e.allowMultiple).withElementAddedAs("expected", e.expected)
    override def finishDeserialization(e: WorkbookElementSerializable): ChoiceInteraction =
      ChoiceInteraction(e.elementId, e.getElementAs[LanguageMapContentId]("prompt"), e.getElementAs[List[LanguageMapContentId]]("options"),
        e.getOptionalElementAs[Boolean]("allowMultiple", false), e.getOptionalElementAs[Option[List[Int]]]("expected", None))
  }
}
