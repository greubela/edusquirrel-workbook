package it.evadid.workbook.elements.interactionElements.codeTaskToggle

import it.evadid.core.util.io.Serializer
import upickle.default.{ReadWriter, macroRW}

case class CodeTaskToggleState(
  isBeginnerMode: Boolean,
  advancedCode: String
)

object CodeTaskToggleState {

  private given rw: ReadWriter[CodeTaskToggleState] = macroRW

  val serializer: Serializer[CodeTaskToggleState] = Serializer.fromUpickleJson(rw)

}
