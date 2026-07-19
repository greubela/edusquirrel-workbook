package it.evadid.homepage.webElements.editor.code.SnapRenderer

import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.naming.CodeRepresentationConfig

class SnapCodeEditorConfig {

  val DisplayConfig = CodeRepresentationConfig(Python, English, skipUnparsable = false)
  val ColorWorkspace = "#f6f8fa"
  val ColorEmpty = "#8c959f"
  val CanvasWidth = 900
  val CanvasHeight = 520
  val Padding = 24.0
  val BlockGap = 8.0
  val Indent = 28.0

}
