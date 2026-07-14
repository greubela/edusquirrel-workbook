package it.evadid.workbook.elements.interactionElements.todo


/*
import it.evadid.core.util.io.Serializer
import upickle.default.{ReadWriter, macroRW}

case class TableFillInState(blankValues: List[String]) {
  def sanitized(blankCount: Int): TableFillInState = TableFillInState(blankValues.take(blankCount).padTo(blankCount, ""))

  def blankValue(blankIndex: Int, blankCount: Int): String = sanitized(blankCount).blankValues.lift(blankIndex).getOrElse("")

  def withBlankValue(blankIndex: Int, value: String, blankCount: Int): TableFillInState = {
    val clean = sanitized(blankCount).blankValues
    if blankIndex < 0 || blankIndex >= blankCount then TableFillInState(clean) else TableFillInState(clean.updated(blankIndex, value))
  }
}

object TableFillInState {
  private given ReadWriter[TableFillInState] = macroRW
  val serializer: Serializer[TableFillInState] = Serializer.fromUpickleJson(summon[ReadWriter[TableFillInState]])
}
*/