package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.steps

import it.evadid.homepage.control.model.FullInfo

import it.evadid.workbook.interaction.sync.UpdateImportance

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorConfig
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditorConfig
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.homepage.workbook.htmlRenderer.workbookEditors.{EditorWithWorkbookPanel, WorkbookEvaEditor}
import it.evadid.workbook.abstractions.{LangMapContentIdType, RoleInWorkbook, TypeOfTextDisplay}
import it.evadid.workbook.elements.displayElements.DisplayLangMapContent
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.SortListExercise
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateJavaStructured, ProgrammingStatePythonStructured, ProgrammingStateRaw}

/** Sorting counterpart of the recreate-turtle renderer: editor, current source and a step-by-step replay. */
case object HtmlSortListExerciseRenderer extends LineBasedRenderingFactory[SortListExercise] {

  /** The answer as text: Snap blocks in their Python form. */
  private[homepage] def source(state: ProgrammingState): String = state match {
    case value: ProgrammingStateJavaStructured => value.toString
    case value: ProgrammingStatePythonStructured => value.toString
    case ProgrammingStateRaw(language, code) if language != it.evadid.core.datastructures.language.AppLanguage.SnapLanguage => code
    // Blocks are shown as their Python form rather than as project XML.
    case other => scala.util.Try(other.toPython.code).getOrElse("")
  }

  /** Overview card and editor side module replaying the sort function's list operations. */
  private[homepage] def sortStepsCard(fullInfo: FullInfo, element: SortListExercise, boundVar: Var[ProgrammingState]): ElementCard =
    ElementCard(fullInfo, LanguageMapContentId("basic/sortStepsTitle"), new SortStepPanel(boundVar, element.sortFunction, element.exampleList,
      key => laminarHelper(fullInfo).plaintextStringSignal(LanguageMapContentId(s"basic/$key"))).getDomElement())

  override protected def createRendering(fullInfo: FullInfo, element: SortListExercise): AtomarLineRendering = {
    val boundVar = element.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR, fullInfo.workbookContextInformation).toAirstreamVar
    val gradingReporter = fullInfo.reporters.reportersFor(element).grading
    def stepsCard = sortStepsCard(fullInfo, element, boundVar)

    def openEditor(): Unit = {
      val instruction = element.instructionText.map(WorkbookEvaEditor.instruction(element.elementId, _))
      val editor = WorkbookEvaEditor(fullInfo, element, boundVar, EvaEditorConfig(enabledLanguages = element.allowedLanguages.toList,
        snapConfig = SnapCodeEditorConfig.forPalette(ProgrammingEditorPalette.NumberList)))
      fullInfo.displayControl.setFullscreen(EditorWithWorkbookPanel(fullInfo, element, instruction, gradingReporter, List(stepsCard), editor))
    }

    val open = HtmlButtonElement.withTextLabel(fullInfo, "basic/OpenEditor", _ => openEditor())
    AtomarLineRendering.cardLine(fullInfo, element, List(
      ElementCard(fullInfo, LanguageMapContentId("basic/openEditor"), open.getDomElement()),
      ElementCard(fullInfo, LanguageMapContentId("basic/staticPreviewProgram"),
        div(cls := "java-full-editor-preview", pre(code(child.text <-- boundVar.signal.map(source))))),
      stepsCard))
  }
}
