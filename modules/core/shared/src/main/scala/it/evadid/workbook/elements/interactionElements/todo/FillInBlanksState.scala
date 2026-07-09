package it.evadid.workbook.elements.interactionElements.todo


/*
import it.evadid.core.util.io.Serializer
import upickle.default.{ReadWriter, macroRW}

case class FillInBlanksState(blankValues: List[String]) {
  def sanitized(blankCount: Int): FillInBlanksState = FillInBlanksState(blankValues.take(blankCount).padTo(blankCount, ""))

  def blankValue(blankIndex: Int, blankCount: Int): String = sanitized(blankCount).blankValues.lift(blankIndex).getOrElse("")

  def withBlankValue(blankIndex: Int, value: String, blankCount: Int): FillInBlanksState = {
    val clean = sanitized(blankCount).blankValues
    if blankIndex < 0 || blankIndex >= blankCount then FillInBlanksState(clean) else FillInBlanksState(clean.updated(blankIndex, value))
  }
}

object FillInBlanksState {
  private given ReadWriter[FillInBlanksState] = macroRW
  val serializer: Serializer[FillInBlanksState] = Serializer.fromUpickleJson(summon[ReadWriter[FillInBlanksState]])
}
*/