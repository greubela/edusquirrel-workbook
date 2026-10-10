package it.evadid.workbook.elements.interactionElements.codeTaskToggle

import it.evadid.core.util.io.Serializer
import upickle.default.{ReadWriter, macroRW}

case class CodeTaskToggleState(
  isBeginnerMode: Boolean,
  advancedCode: String
) derives upickle.default.ReadWriter

object CodeTaskToggleState {


  val serializer: Serializer[CodeTaskToggleState] = Serializer.fromUpickleJson(summon[ReadWriter[CodeTaskToggleState]])

}
