package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.ExecutionMethod
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.core.datastructures.vectorShapes.renderer.{SvgLaminarRenderer, VmToSvg}
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder
import it.evadid.homepage.webElements.basic.{HtmlButtonElement, HtmlImageElement}
import it.evadid.homepage.webElements.editor.code.{EvaEditor, EvaEditorConfig}
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditorConfig
import it.evadid.homepage.webElements.editor.code.TurtleEditor.EvaTurtleEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic.HtmlBasicCheckboxRenderer.fullInfo
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic.HtmlProgrammingExerciseRenderer.fullInfo
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.elements.interactionElements.Turtle.TurtleRecreateShapeInteraction
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingEditorPalette, ProgrammingExercise, ProgrammingState}
import it.evadid.workbook.interaction.sync.UpdateImportance
import todomove.datastructures.web.file.FullImage

import scala.util.{Failure, Success}

case object HtmlTurtleRecreateShapeRenderer extends LineBasedRenderingFactory[TurtleRecreateShapeInteraction] {

  private def createInteractivePreview(boundVar: Var[ProgrammingState], workbookElement: TurtleRecreateShapeInteraction): ElementCard = {
    val cmd = boundVar.signal.map(_.toBeExpressionState.deriveTurtleCommands)

    ElementCard(
      LanguageMapContentId("basic/gradingPreviewProgram"),
      TurtleJsxGraphRenderer.render(cmd, workbookElement.desiredResult)
    )
  }

  override protected def createRendering(workbookElement: TurtleRecreateShapeInteraction): AtomarLineRendering = {
    val boundVar = workbookElement.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR).toAirstreamVar

    val snapEditorConfig: SnapCodeEditorConfig = workbookElement.availablePalette match
      case ProgrammingEditorPalette.Default => SnapCodeEditorConfig.Testing
      case ProgrammingEditorPalette.PythonCompatibleSnap => SnapCodeEditorConfig.PythonCompatibleTesting
      case ProgrammingEditorPalette.BeginnerTurtle => SnapCodeEditorConfig.BeginnerTurtleTesting
      case ProgrammingEditorPalette.Embroidery => SnapCodeEditorConfig.EmbroideryTesting

    val editorConfig = EvaEditorConfig(snapConfig = snapEditorConfig)
    val editor = EvaTurtleEditor(boundVar, editorConfig, createInteractivePreview(boundVar, workbookElement))

    def buttonPressed(): Unit = fullInfo.displayControl.setFullscreen(editor)

    val button: HtmlButtonElement = HtmlButtonElement.withTextLabel("basic/OpenEditor", event => buttonPressed())
    val buttonCard = ElementCard(LanguageMapContentId("basic/openEditor"), button.getDomElement())

    AtomarLineRendering.cardLine(workbookElement, List(buttonCard, createInteractivePreview(boundVar, workbookElement)))
  }
}