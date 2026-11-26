package it.evadid.homepage.webElements.editor.blocks

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.vectorShapes.blocks.ControlFlowDisplay
import it.evadid.vm.editing.BeValueInput.Kind

/** UI strings of the block editor; supply them with [[fromContent]] from a workbook renderer. */
final case class BlockEditorLabels(
    tab: Signal[String],
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
      tab = label("blockeditor/blocksTab"),
      library = label("blockeditor/library"),
      libraryHint = label("blockeditor/libraryHint"),
      program = label("blockeditor/program"),
      placeholders = label("blockeditor/placeholders"),
      controlFlow = label("blockeditor/controlFlow"),
      controlFlowOption = {
        case ControlFlowDisplay.Hidden => label("blockeditor/flowHidden")
        case ControlFlowDisplay.Backgrounds => label("blockeditor/flowBackgrounds")
        case ControlFlowDisplay.Full => label("blockeditor/flowFull")
      },
      editValue = label("blockeditor/editValue"),
      apply = label("blockeditor/apply"),
      cancel = label("blockeditor/cancel"),
      invalid = {
        case Kind.Number => label("blockeditor/invalidNumber")
        case Kind.Integer => label("blockeditor/invalidInteger")
        case Kind.Boolean => label("blockeditor/invalidBoolean")
        case Kind.Text => label("blockeditor/invalidText")
        case Kind.Date => label("blockeditor/invalidDate")
        case Kind.AnyValue => label("blockeditor/invalidAnyValue")
      }
    )
  }
}
