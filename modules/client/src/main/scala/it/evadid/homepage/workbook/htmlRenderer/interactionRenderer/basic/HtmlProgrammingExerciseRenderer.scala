package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import it.evadid.homepage.control.model.FullInfo

import it.evadid.workbook.interaction.sync.UpdateImportance

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.core.datastructures.vectorShapes.renderer.{SvgLaminarRenderer, VmToSvg}
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorConfig
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapPreviewEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.StaticTurtleDrawing
import it.evadid.homepage.workbook.htmlRenderer.workbookEditors.{EditorWithWorkbookPanel, WorkbookEvaEditor}
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExercise

import scala.util.Try

case object HtmlProgrammingExerciseRenderer extends LineBasedRenderingFactory[ProgrammingExercise] {

  override protected def createRendering(fullInfo: FullInfo, workbookElement: ProgrammingExercise): AtomarLineRendering = {

    val boundVar = fullInfo.interactions.boundVar(workbookElement, UpdateImportance.MAJOR)

    val editorConfig: SnapCodeEditorConfig = SnapCodeEditorConfig.forPalette(workbookElement.editorPalette)

    val gradingReporter = fullInfo.reporters.reportersFor(workbookElement).grading

    def editor = WorkbookEvaEditor(fullInfo, workbookElement, boundVar, EvaEditorConfig(snapConfig = editorConfig))

    def buttonPressed(): Unit = {
      val wrappedEditor = EditorWithWorkbookPanel(fullInfo, workbookElement, None, gradingReporter, List(), editor)
      fullInfo.displayControl.setFullscreen(wrappedEditor)
    }

    val button: HtmlButtonElement = HtmlButtonElement.withTextLabel(fullInfo, "basic/OpenEditor", event => buttonPressed())
    val buttonCard = ElementCard(fullInfo, LanguageMapContentId("basic/openEditor"), button.getDomElement())

    val previewEditor = SnapPreviewEditor(boundVar, editorConfig, fullInfo.signals.currentLanguage)
    val canvasCard = ElementCard(fullInfo, LanguageMapContentId("basic/canvas"), previewEditor.getDomElement())

    // static preview based on the custom display engine (not working yet, for test purposes)
    val shapeLogger = Logger.withNameAndPrefixes(
      Some("HtmlProgrammingExerciseRenderer::ShapeRenderingLogger"),
      PrintToStdLogger.printEverything
    )
    val staticRendering = ElementCard(
      fullInfo, LanguageMapContentId("basic/staticPreviewProgram"),
      Try(SvgLaminarRenderer.render(shapeLogger, VmToSvg.renderBeExpression(shapeLogger, boundVar.now().inEnvironment(workbookElement.environment).expression.expression)))
        .getOrElse(div(child.text <-- laminarHelper(fullInfo).plaintextStringSignal(LanguageMapContentId("basic/gradingError"))))
    )

    // Drawings are static: the worker derives them from the shared BeExpression for every language.
    val runCard = ElementCard(fullInfo, LanguageMapContentId("basic/turtleOutput"),
      StaticTurtleDrawing(boundVar.signal, workbookElement.environment).getDomElement())

    AtomarLineRendering.cardLine(fullInfo, workbookElement,
      List(buttonCard, canvasCard, staticRendering, runCard))
  }
}
