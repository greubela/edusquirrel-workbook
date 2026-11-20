package it.evadid.homepage.webElements.editor.blocks

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.vectorShapes.blocks.ControlFlowDisplay
import it.evadid.vm.editing.BeLiteralInput.Kind

/** UI strings of the block editor; supply them with [[fromContent]] from a workbook renderer. */
final case class BlockEditorLabels(
    library: Signal[String],
    libraryHint: Signal[String],
    program: Signal[String],
    placeholders: Signal[String],
    controlFlow: Signal[String],
    controlFlowOption: ControlFlowDisplay => Signal[String],
    editValue: Signal[String],
    apply: Signal[String],
    cancel: Signal[String],
    invalid: Kind => Signal[String]
)

object BlockEditorLabels {
  def fromContent(text: LanguageMapContentId => Signal[String]): BlockEditorLabels = {
    def label(key: String) = text(LanguageMapContentId(key))
    BlockEditorLabels(
      library = label("basic/blockEditorLibrary"),
      libraryHint = label("basic/blockEditorLibraryHint"),
      program = label("basic/blockEditorProgram"),
      placeholders = label("basic/blockEditorPlaceholders"),
      controlFlow = label("basic/blockEditorControlFlow"),
      controlFlowOption = {
        case ControlFlowDisplay.Hidden => label("basic/blockEditorFlowHidden")
        case ControlFlowDisplay.Backgrounds => label("basic/blockEditorFlowBackgrounds")
        case ControlFlowDisplay.Full => label("basic/blockEditorFlowFull")
      },
      editValue = label("basic/blockEditorEditValue"),
      apply = label("basic/blockEditorApply"),
      cancel = label("basic/blockEditorCancel"),
      invalid = {
        case Kind.Number => label("basic/blockEditorInvalidNumber")
        case Kind.Integer => label("basic/blockEditorInvalidInteger")
        case Kind.Boolean => label("basic/blockEditorInvalidBoolean")
        case Kind.Text => label("basic/blockEditorInvalidText")
        case Kind.Date => label("basic/blockEditorInvalidDate")
        case Kind.AnyValue => label("basic/blockEditorInvalidAnyValue")
      }
    )
  }
}
