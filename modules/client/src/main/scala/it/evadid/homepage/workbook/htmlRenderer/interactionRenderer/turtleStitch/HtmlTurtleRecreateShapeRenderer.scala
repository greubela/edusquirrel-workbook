package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditorConfig, EvaEditorTurtle}
import it.evadid.homepage.webElements.editor.code.JavaTurtleEditorExtension
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapPreviewEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateJavaString, ProgrammingStatePythonString}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleDrawingPolicy, TurtleGraphic}
import it.evadid.workbook.interaction.sync.UpdateImportance
import scala.util.Try

case object HtmlTurtleRecreateShapeRenderer extends LineBasedRenderingFactory[TurtleRecreateShapeInteraction] {

  private[turtleStitch] def hasProgram(state: ProgrammingState): Boolean = state match {
    case ProgrammingStateJavaString(code) => code.trim.nonEmpty
    case ProgrammingStatePythonString(code) => code.trim.nonEmpty
    case other => Try(other.toPython.code.trim.nonEmpty).getOrElse(true)
  }

  private[turtleStitch] def commandsForPreview(state: ProgrammingState): Try[List[TurtleCommand[Double]]] =
    Try(state match
      case java: ProgrammingStateJavaString => java.toLegacyTurtleCommands
      case _ => state.toBeExpressionState.deriveTurtleCommands
    )

  def createInteractivePreview(boundVar: Var[ProgrammingState], expected: TurtleGraphic,
      policy: TurtleDrawingPolicy = TurtleDrawingPolicy.Strokes): ElementCard = {
    ElementCard(
      LanguageMapContentId("basic/turtleGradingPanel"),
      div(child <-- boundVar.signal.map { state =>
        commandsForPreview(state).flatMap(commands => Try(TurtleDrawingGrading.assess(commands, expected, policy)))
          .map(result => TurtleJsxGraphRenderer.render(result.scene, "Turtle drawing"))
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
    val editor = EvaEditorTurtle(boundVar, editorConfig, workbookElement.desiredResult,
      editorExtensions = List(new JavaTurtleEditorExtension(boundVar)), comparisonPolicy = workbookElement.comparisonPolicy)

    lazy val codePreview = SnapPreviewEditor(boundVar, snapEditorConfig).getDomElement()
    val programPreview = boundVar.signal.map(hasProgram).distinct.map { hasProgram =>
      if hasProgram then codePreview
      else p(text <-- laminarHelper.plaintextStringSignal("basic/turtleNoProgramYet"))
    }
    val canvasCard = ElementCard(LanguageMapContentId("basic/canvas"), programPreview)

    def buttonPressed(): Unit = fullInfo.displayControl.setFullscreen(editor)

    val button: HtmlButtonElement = HtmlButtonElement.withTextLabel("basic/OpenEditor", event => buttonPressed())
    val buttonCard = ElementCard(LanguageMapContentId("basic/openEditor"), button.getDomElement())

    AtomarLineRendering.cardLine(workbookElement, List(buttonCard, canvasCard,
      createInteractivePreview(boundVar, workbookElement.desiredResult, workbookElement.comparisonPolicy)))
  }
}
