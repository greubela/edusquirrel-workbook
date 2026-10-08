package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditorConfig, EvaEditorTurtle}
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapPreviewEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.Turtle.TurtleRecreateShapeInteraction
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingEditorPalette, ProgrammingState, TurtleGraphic}
import it.evadid.workbook.interaction.sync.UpdateImportance

case object HtmlTurtleRecreateShapeRenderer extends LineBasedRenderingFactory[TurtleRecreateShapeInteraction] {

  def createInteractivePreview(boundVar: Var[ProgrammingState], expected: TurtleGraphic): ElementCard = {
    val cmd = boundVar.signal.map(_.toBeExpressionState.deriveTurtleCommands)

    ElementCard(
      LanguageMapContentId("basic/turtleGradingPanel"),
      TurtleJsxGraphRenderer.render(cmd, expected)
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