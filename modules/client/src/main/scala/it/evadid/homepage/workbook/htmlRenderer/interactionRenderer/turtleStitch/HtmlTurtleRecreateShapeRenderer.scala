package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditorConfig, EvaEditorTurtle}
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapPreviewEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.Turtle.TurtleRecreateShapeInteraction
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingEditorPalette, ProgrammingState, ProgrammingStateJavaString, TurtleGraphic}
import it.evadid.workbook.interaction.sync.UpdateImportance
import scala.util.Try

case object HtmlTurtleRecreateShapeRenderer extends LineBasedRenderingFactory[TurtleRecreateShapeInteraction] {

  private[turtleStitch] def commandsForPreview(state: ProgrammingState): Try[List[TurtleCommand[Double]]] =
    Try(state match
      case java: ProgrammingStateJavaString => java.toLegacyTurtleCommands
      case _ => state.toBeExpressionState.deriveTurtleCommands
    )

  def createInteractivePreview(boundVar: Var[ProgrammingState], expected: TurtleGraphic): ElementCard = {
    ElementCard(
      LanguageMapContentId("basic/gradingPreviewProgram"),
      div(child <-- boundVar.signal.map { state =>
        commandsForPreview(state).map(TurtleJsxGraphRenderer.render(_, expected))
          .getOrElse(div("Preview unavailable for this draft."))
      })
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
    val editor = EvaEditorTurtle(boundVar, editorConfig, workbookElement.desiredResult)

    val canvasCard = ElementCard(LanguageMapContentId("basic/canvas"), SnapPreviewEditor(boundVar, snapEditorConfig).getDomElement())

    def buttonPressed(): Unit = fullInfo.displayControl.setFullscreen(editor)

    val button: HtmlButtonElement = HtmlButtonElement.withTextLabel("basic/OpenEditor", event => buttonPressed())
    val buttonCard = ElementCard(LanguageMapContentId("basic/openEditor"), button.getDomElement())

    AtomarLineRendering.cardLine(workbookElement, List(buttonCard, canvasCard, createInteractivePreview(boundVar, workbookElement.desiredResult)))
  }
}
