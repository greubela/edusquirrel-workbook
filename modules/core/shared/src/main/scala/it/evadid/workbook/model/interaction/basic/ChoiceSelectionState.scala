package it.evadid.workbook.model.interaction.basic

import it.evadid.core.util.io.Serializer
import upickle.default.{ReadWriter, macroRW}

/** State shared by single- and multiple-choice workbook interactions. */
case class ChoiceSelectionState(selectedOptionIndices: List[Int]) {
  def sanitized(optionCount: Int, allowMultiple: Boolean): ChoiceSelectionState = {
    val valid = selectedOptionIndices.distinct.filter(index => index >= 0 && index < optionCount)
    ChoiceSelectionState(if allowMultiple then valid else valid.take(1))
  }

  def isSelected(optionIndex: Int, optionCount: Int, allowMultiple: Boolean): Boolean =
    sanitized(optionCount, allowMultiple).selectedOptionIndices.contains(optionIndex)

  def withSingleSelection(optionIndex: Int, optionCount: Int): ChoiceSelectionState =
    if optionIndex >= 0 && optionIndex < optionCount then ChoiceSelectionState(List(optionIndex)) else sanitized(optionCount, allowMultiple = false)

  def withToggledSelection(optionIndex: Int, optionCount: Int): ChoiceSelectionState = {
    val clean = sanitized(optionCount, allowMultiple = true).selectedOptionIndices
    if optionIndex < 0 || optionIndex >= optionCount then ChoiceSelectionState(clean)
    else if clean.contains(optionIndex) then ChoiceSelectionState(clean.filterNot(_ == optionIndex))
    else ChoiceSelectionState(clean :+ optionIndex)
  }
}

object ChoiceSelectionState {
  private given ReadWriter[ChoiceSelectionState] = macroRW
  val serializer: Serializer[ChoiceSelectionState] = Serializer.fromUpickleJson(summon[ReadWriter[ChoiceSelectionState]])
}
