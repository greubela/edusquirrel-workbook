package it.evadid.workbook.elements.interactionElements.codeTaskToggle

import it.evadid.core.util.io.Serializer
import upickle.default.*

case class CodeTaskToggleState(
  isBeginnerMode: Boolean,
  advancedCode: String
) derives ReadWriter

