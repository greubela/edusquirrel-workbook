package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import it.evadid.vm.conversion.ProgrammingStateConversions.*

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle.JavaTurtleEditorExtension
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditor, EvaEditorConfig, EvaEditorLabels, *}
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapPreviewEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanel
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleDrawingPolicy, TurtleGraphic}
import it.evadid.workbook.interaction.sync.UpdateImportance

import scala.util.Try
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

case object HtmlTurtleRecreateShapeRenderer extends LineBasedRenderingFactory[TurtleRecreateShapeInteraction] {

  private[turtleStitch] def hasProgram(state: ProgrammingState): Boolean = state match {
    case ProgrammingStateJavaString(code) => code.trim.nonEmpty
    case ProgrammingStatePythonString(code) => code.trim.nonEmpty
    case other => Try(other.toPython.code.trim.nonEmpty).getOrElse(true)
  }

  private[turtleStitch] def commandsForPreview(state: ProgrammingState): Try[List[TurtleCommand[Double]]] =
    Try(state match
      case java: ProgrammingStateJavaString => java.toLegacyTurtleCommands
      case _ => state.toBeExpression.turtleCommandExecutionInfo.completedCommands
    )

  def createInteractivePreview(boundVar: Var[ProgrammingState], expected: TurtleGraphic,
                               policy: TurtleDrawingPolicy = TurtleDrawingPolicy.Strokes): ElementCard = {
    ElementCard(
      LanguageMapContentId("basic/turtleGradingPanel"),
      div(child <-- boundVar.signal.flatMapSwitch { state =>
        EventStream.fromFuture(Future {
        commandsForPreview(state).flatMap(commands => Try(TurtleDrawingGrading.assess(commands, expected, policy)))
          .map(result => TurtleJsxGraphRenderer.render(result.scene, "Turtle drawing"))
          .getOrElse(div("Preview unavailable for this draft."))
        })
      }.startWith(div("Preparing drawing…")))
    )
  }

  override protected def createRendering(workbookElement: TurtleRecreateShapeInteraction): AtomarLineRendering = {
    val boundVar = workbookElement.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR, fullInfo.workbookContextInformation).toAirstreamVar

    val snapEditorConfig: SnapCodeEditorConfig = SnapCodeEditorConfig.forPalette(workbookElement.availablePalette)

    val editorConfig = EvaEditorConfig(snapConfig = snapEditorConfig)
    def editor = EvaEditor(fullInfo.loggerSystemInfo.uiAndDomLogger, boundVar, List(new it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle.TurtleTaskEditorExtension(
        boundVar, Some(workbookElement.desiredResult), workbookElement.comparisonPolicy)), editorConfig, humanLanguage = fullInfo.signals.currentLanguage,
        labels = EvaEditorLabels.fromContent(laminarHelper.plaintextStringSignal(_)))

    lazy val codePreview = SnapPreviewEditor(boundVar, snapEditorConfig, fullInfo.signals.currentLanguage).getDomElement()
    val programPreview = boundVar.signal.map(hasProgram).distinct.map { hasProgram =>
      if hasProgram then codePreview
      else p(text <-- laminarHelper.plaintextStringSignal("basic/turtleNoProgramYet"))
    }
    val canvasCard = ElementCard(LanguageMapContentId("basic/canvas"), programPreview)

    def buttonPressed(): Unit = fullInfo.displayControl.setFullscreen(InteractionInfoPanel.withEditor(workbookElement, editor))

    val button: HtmlButtonElement = HtmlButtonElement.withTextLabel("basic/OpenEditor", event => buttonPressed())
    val buttonCard = ElementCard(LanguageMapContentId("basic/openEditor"), button.getDomElement())

    AtomarLineRendering.cardLine(workbookElement, List(buttonCard, canvasCard,
      createInteractivePreview(boundVar, workbookElement.desiredResult, workbookElement.comparisonPolicy)))
  }
}
