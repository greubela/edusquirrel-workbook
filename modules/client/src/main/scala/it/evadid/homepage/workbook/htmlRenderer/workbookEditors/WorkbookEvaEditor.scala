package it.evadid.homepage.workbook.htmlRenderer.workbookEditors

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditor, EvaEditorConfig, EvaEditorEnvironment, EvaEditorLabels}
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.workbook.abstractions.{LangMapContentIdType, RoleInWorkbook, TypeOfTextDisplay}
import it.evadid.workbook.elements.displayElements.DisplayLangMapContent
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

/** The single way workbook renderers create Eva: page logging, localized labels and the display language. */
object WorkbookEvaEditor {
  def environment(fullInfo: FullInfo): EvaEditorEnvironment = EvaEditorEnvironment(
    fullInfo.loggerSystemInfo.uiAndDomLogger,
    EvaEditorLabels.fromContent(LaminarRenderHelper.singleton.plaintextStringSignal),
    fullInfo.signals.currentLanguage)

  def apply(fullInfo: FullInfo, answer: Var[ProgrammingState], config: EvaEditorConfig): EvaEditor =
    EvaEditor(answer, Var(config), environment(fullInfo))

  /** The exercise text shown in the editor's side panel. */
  def instruction(elementId: String, text: LanguageMapContentId): DisplayLangMapContent =
    DisplayLangMapContent(elementId + "-sidebar-instruction", text,
      LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.MARKDOWN))
}
