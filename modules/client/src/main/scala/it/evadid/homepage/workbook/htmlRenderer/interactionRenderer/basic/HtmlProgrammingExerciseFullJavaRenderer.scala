package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import it.evadid.homepage.control.model.FullInfo

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
import it.evadid.homepage.control.info.interaction.GradingReporter
import it.evadid.workbook.abstractions.{LangMapContentIdType, RoleInWorkbook, TypeOfTextDisplay}
import it.evadid.workbook.elements.displayElements.DisplayLangMapContent

case object HtmlProgrammingExerciseFullJavaRenderer extends LineBasedRenderingFactory[ProgrammingExerciseFullJava] {
  private[homepage] def editorState(fullInfo: FullInfo, element: ProgrammingExerciseFullJava): Var[ProgrammingState] =
    fullInfo.interactions.boundVar(element, UpdateImportance.MAJOR)

  private[homepage] def createNewEditor(
                                         fullInfo: FullInfo,
                                         element: ProgrammingExerciseFullJava,
                                         boundVar: Var[ProgrammingState],
                                         gradingReporter: GradingReporter
                                       ):
  EditorWithWorkbookPanel[ProgrammingExerciseWithLlmScaffoldingAndGrading.type, EvaEditorConfig] = {

    val previewCards = element.dataView.toSeq.map { view =>
      ElementCard(fullInfo, LanguageMapContentId("basic/programDataTitle"),
        new ProgramDataPreview(boundVar, view, key => laminarHelper(fullInfo).plaintextStringSignal(LanguageMapContentId(s"basic/$key"))).getDomElement())
    } ++ element.gradingElements.collect {
      case grader: it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.RecreateShapeGrader =>
        ElementCard(fullInfo, LanguageMapContentId("basic/turtleGradingPanel"),
          it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.CompareShapePreview(
            fullInfo, boundVar.signal, grader.desiredResult, grader.comparisonPolicy, element.environment).getDomElement())
    }
    val instruction = element.instructionText.map(WorkbookEvaEditor.instruction(element.elementId, _))
    val evaEditor = WorkbookEvaEditor(fullInfo, element, boundVar, EvaEditorConfig(enabledLanguages = List(AppLanguage.Java)))
    EditorWithWorkbookPanel(fullInfo, element, instruction, gradingReporter, previewCards, evaEditor)
  }

  override protected def createRendering(fullInfo: FullInfo, workbookElement: ProgrammingExerciseFullJava): AtomarLineRendering = {
    val boundVar = editorState(fullInfo, workbookElement)
    val gradingReporter = fullInfo.reporters.reportersFor(workbookElement).grading

    val openButton = HtmlButtonElement.withTextLabel(
      fullInfo, "basic/OpenEditor", _ => fullInfo.displayControl.setFullscreen(createNewEditor(fullInfo, workbookElement, boundVar, gradingReporter))
    )
    val buttonCard = ElementCard(fullInfo, LanguageMapContentId("basic/openEditor"), openButton.getDomElement())
    val editorCard = ElementCard(
      fullInfo, LanguageMapContentId("basic/staticPreviewProgram"),
      div(
        cls := "java-full-editor-preview",
        h3("Java program"),
        pre(code(child.text <-- boundVar.signal.map(_.toJava.code)))
      )
    )

    AtomarLineRendering.cardLine(fullInfo, workbookElement, List(buttonCard, editorCard))
  }
}
