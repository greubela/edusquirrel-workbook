package it.evadid.homepage.workbook.htmlRenderer.workbookEditors

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditor, EvaEditorConfig, EvaEditorEnvironment, EvaEditorLabels}
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.workbook.abstractions.{LangMapContentIdType, RoleInWorkbook, TypeOfTextDisplay, WorkbookElement}
import it.evadid.workbook.elements.displayElements.DisplayLangMapContent
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExerciseEnvironment
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

/** The single way workbook renderers create Eva: page logging, localized labels, the display language, the
  * exercise's programming environment and its id, which names project downloads.
  */
object WorkbookEvaEditor {
  type Exercise = WorkbookElement & ProgrammingExerciseEnvironment

  def environment(fullInfo: FullInfo, exercise: Exercise): EvaEditorEnvironment = EvaEditorEnvironment(
    fullInfo.loggerSystemInfo.uiAndDomLogger,
    EvaEditorLabels.fromContent(LaminarRenderHelper.singleton.plaintextStringSignal),
    fullInfo.signals.currentLanguage,
    exercise.environment,
    exercise.elementId)

  def apply(fullInfo: FullInfo, exercise: Exercise, answer: Var[ProgrammingState], config: EvaEditorConfig): EvaEditor =
    EvaEditor(answer, Var(config), environment(fullInfo, exercise))

  /** The exercise text shown in the editor's side panel. */
  def instruction(elementId: String, text: LanguageMapContentId): DisplayLangMapContent =
    DisplayLangMapContent(elementId + "-sidebar-instruction", text,
      LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.MARKDOWN))
}
