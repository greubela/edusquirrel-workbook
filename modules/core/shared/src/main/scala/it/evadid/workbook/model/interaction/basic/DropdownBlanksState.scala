package it.evadid.workbook.model.interaction.basic

import it.evadid.core.util.io.Serializer
import upickle.default.{ReadWriter, macroRW}

case class DropdownBlanksState(selectedOptionIndicesByBlankIndex: List[Option[Int]]) {
  def sanitized(optionCountsByBlank: List[Int]): DropdownBlanksState = {
    val blankCount = optionCountsByBlank.size
    val padded = selectedOptionIndicesByBlankIndex.take(blankCount).padTo(blankCount, None)
    DropdownBlanksState(padded.zip(optionCountsByBlank).map { case (selected, optionCount) => selected.filter(index => index >= 0 && index < optionCount) })
  }

  def selectedOptionIndex(blankIndex: Int, optionCountsByBlank: List[Int]): Option[Int] =
    sanitized(optionCountsByBlank).selectedOptionIndicesByBlankIndex.lift(blankIndex).flatten

  def withSelection(blankIndex: Int, optionIndex: Option[Int], optionCountsByBlank: List[Int]): DropdownBlanksState = {
    val clean = sanitized(optionCountsByBlank).selectedOptionIndicesByBlankIndex
    if blankIndex < 0 || blankIndex >= optionCountsByBlank.size then DropdownBlanksState(clean)
    else DropdownBlanksState(clean.updated(blankIndex, optionIndex.filter(index => index >= 0 && index < optionCountsByBlank(blankIndex))))
  }
}

object DropdownBlanksState {
  private given ReadWriter[DropdownBlanksState] = macroRW
  val serializer: Serializer[DropdownBlanksState] = Serializer.fromUpickleJson(summon[ReadWriter[DropdownBlanksState]])
}
