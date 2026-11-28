package it.evadid.workbook.interactions.todo

import it.evadid.core.util.io.Serializer
import upickle.default.{ReadWriter, macroRW}

case class DropdownBlanksState(selectedOptionIndicesByBlankIndex: Seq[Option[Int]]) derives upickle.default.ReadWriter {
  def sanitized(optionCountsByBlank: Seq[Int]): DropdownBlanksState = {
    val blankCount = optionCountsByBlank.size
    val padded = selectedOptionIndicesByBlankIndex.take(blankCount).padTo(blankCount, None)
    DropdownBlanksState(padded.zip(optionCountsByBlank).map { case (selected, optionCount) => selected.filter(index => index >= 0 && index < optionCount) })
  }

  def selectedOptionIndex(blankIndex: Int, optionCountsByBlank: Seq[Int]): Option[Int] =
    sanitized(optionCountsByBlank).selectedOptionIndicesByBlankIndex.lift(blankIndex).flatten

  def withSelection(blankIndex: Int, optionIndex: Option[Int], optionCountsByBlank: Seq[Int]): DropdownBlanksState = {
    val clean = sanitized(optionCountsByBlank).selectedOptionIndicesByBlankIndex
    if blankIndex < 0 || blankIndex >= optionCountsByBlank.size then DropdownBlanksState(clean)
    else DropdownBlanksState(clean.updated(blankIndex, optionIndex.filter(index => index >= 0 && index < optionCountsByBlank(blankIndex))))
  }
}

object DropdownBlanksState {
  val serializer: Serializer[DropdownBlanksState] = Serializer.fromUpickleJson(summon[ReadWriter[DropdownBlanksState]])
}
