package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import it.evadid.workbook.interaction.sync.UpdateImportance

import it.evadid.workbook.abstractions.InteractionFormat.{ProgrammingExerciseWithLlmScaffoldingAndGrading}

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection.DomElementSingle
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.homepage.workbook.htmlRenderer.workbookEditors.{EditorWithWorkbookPanel, WorkbookEvaEditor}
import it.evadid.workbook.abstractions.scaffolding.AdditionalInfoLlmScaffolding
import it.evadid.workbook.elements.interactionElements.programming.AdditionalProgrammingTestInfo
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExerciseFullJava
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleDrawingPolicy
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.interaction.sync.SyncControl
import it.evadid.homepage.control.info.interaction.GradingReporter
import it.evadid.workbook.abstractions.{LangMapContentIdType, RoleInWorkbook, TypeOfTextDisplay}
import it.evadid.workbook.elements.displayElements.DisplayLangMapContent

case object HtmlProgrammingExerciseFullJavaRenderer extends LineBasedRenderingFactory[ProgrammingExerciseFullJava] {
  private[homepage] def editorState(element: ProgrammingExerciseFullJava, syncControl: SyncControl): Var[ProgrammingState] =
    element.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR, fullInfo.workbookContextInformation).toAirstreamVar

  private[homepage] def createNewEditor(
                                         element: ProgrammingExerciseFullJava,
                                         boundVar: Var[ProgrammingState],
                                         gradingReporter: GradingReporter
                                       ):
  EditorWithWorkbookPanel[ProgrammingExerciseWithLlmScaffoldingAndGrading.type, EvaEditorConfig] = {

    val previewCards = element.dataView.toSeq.map { view =>
      ElementCard(LanguageMapContentId("basic/programDataTitle"),
        new ProgramDataPreview(boundVar, view, key => laminarHelper.plaintextStringSignal(LanguageMapContentId(s"basic/$key"))).getDomElement())
    } ++ element.gradingElements.collect {
      case grader: it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.RecreateShapeGrader =>
        ElementCard(LanguageMapContentId("basic/turtleGradingPanel"),
          it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.CompareShapePreview(
            boundVar, grader.desiredResult, grader.comparisonPolicy).getDomElement())
    }
    val instruction = element.instructionText.map(WorkbookEvaEditor.instruction(element.elementId, _))
    val evaEditor = WorkbookEvaEditor(fullInfo, boundVar, EvaEditorConfig(enabledLanguages = List(AppLanguage.Java)))
    EditorWithWorkbookPanel(element, instruction, gradingReporter, previewCards, evaEditor)
  }

  override protected def createRendering(workbookElement: ProgrammingExerciseFullJava): AtomarLineRendering = {
    val boundVar = editorState(workbookElement, fullInfo.syncControl)
    val gradingReporter = fullInfo.signals.reportersFor(workbookElement).grading

    val openButton = HtmlButtonElement.withTextLabel(
      "basic/OpenEditor", _ => fullInfo.displayControl.setFullscreen(createNewEditor(workbookElement, boundVar, gradingReporter))
    )
    val buttonCard = ElementCard(LanguageMapContentId("basic/openEditor"), openButton.getDomElement())
    val editorCard = ElementCard(
      LanguageMapContentId("basic/staticPreviewProgram"),
      div(
        cls := "java-full-editor-preview",
        h3("Java program"),
        pre(code(child.text <-- boundVar.signal.map(_.toJava.code)))
      )
    )

    AtomarLineRendering.cardLine(workbookElement, List(buttonCard, editorCard))
  }
}
