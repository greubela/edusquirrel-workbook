package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapExpressionBridge.{LibraryBlock, LibraryTab}
import it.evadid.vm.BeProgram
import it.evadid.vm.code.BeExpression
import it.evadid.vm.naming.CodeRepresentationConfig
import it.evadid.vm.types.BeDataType


object SnapCodeEditorConfig {
  /** Minimal configuration used by integration tests and manual smoke tests. */
  val Testing: SnapCodeEditorConfig = SnapCodeEditorConfig(
    showElements = SnapEditorShowElements(
      showHeadline = false,
      showPalette = true,
      showStage = false,
      showSpriteControl = false
    ),
    libraryTabs = SnapExpressionBridge.testBlocks
  )


  case class SnapEditorVisuals(
                                ColorEmpty: String = "#8c959f",
                                CanvasWidth: Int = 900,
                                CanvasHeight: Int = 520,
                                Padding: Double = 24.0,
                                BlockGap: Double = 8.0,
                                Indent: Double = 28.0
                              )


  case class SnapEditorAllowInteraction(
                                         allowCustomBlocks: Boolean = true,
                                       )

  case class SnapEditorShowElements(
                                     showHeadline: Boolean = true, // file saving etc
                                     showPalette: Boolean = true, // library
                                     showPaletteCategories: Boolean = true, //
                                     showStage: Boolean = true, // executable area right
                                     showSpriteControl: Boolean = true // scripts / costumes / sound headline
                                   )

  case class SnapCodeEditorConfig(
                                   control: SnapEditorAllowInteraction = SnapEditorAllowInteraction(),
                                   showElements: SnapEditorShowElements = SnapEditorShowElements(),
                                   libraryTabs: List[LibraryTab] = List(),
                                   codeRepresentation: CodeRepresentationConfig = CodeRepresentationConfig(Python, English, skipUnparsable = false),
                                   visuals: SnapEditorVisuals = SnapEditorVisuals()
                                 )

}
