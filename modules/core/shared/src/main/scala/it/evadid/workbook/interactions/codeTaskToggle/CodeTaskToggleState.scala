package it.evadid.workbook.interactions.codeTaskToggle

import it.evadid.core.util.io.Serializer
import upickle.default.*

case class CodeTaskToggleState(
  isBeginnerMode: Boolean,
  advancedCode: String
) derives ReadWriter

