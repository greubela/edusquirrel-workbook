package it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.ElementCard
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGraphic
import org.scalajs.dom
import org.scalajs.dom.html.Canvas

/**
 * FEATURE: Snap fullscreen turtle stage panel (Execute → live Scratch-paced green-flag).
 *
 * Removable as a unit:
 *  1. Delete this file (`SnapTurtleStagePanel.scala`); keep `SnapTurtleStage.scala` if the
 *     workbook-page Run card still needs the final-PNG worker path
 *  2. Remove the panel child from `SnapCodeEditor.getDomElement()`
 *  3. Delete the CSS block marked `FEATURE: SnapTurtleStagePanel` in
 *     `homepage/css/workbook/workbook-interactions.css` (and fullscreen shell rules
 *     in `workbook-structure.css` if unused)
 *  4. Remove `runGreenFlagOnStage` / `stopGreenFlagOnStage` from the Snap impl if unused
 *
 * Execute uses Scratch frame yields with a configurable pause per block
 * (toolbar Speed ms → Process.flashTime) so loops stay watchable.
 */
object SnapTurtleStagePanel {

  /**
   * Right-hand turtle panel for the fullscreen Snap shell.
   *
   * @param flushPending call before run so Snap XML edits are published
   * @param runOnStage   green-flag the live IDE stage and mirror frames onto the canvas
   * @param stopRun      stop scripts / cancel mirroring (e.g. on unmount)
   */
  def chrome(
              editor: SnapCodeEditor,
              flushPending: () => Unit,
              runOnStage: Canvas => Unit,
              stopRun: () => Unit
            ): L.Element = {
    val laminarHelper = LaminarRenderHelper.singleton
    var stageCanvas: Option[Canvas] = None

    def execute(): Unit = {
      flushPending()
      stageCanvas.foreach(runOnStage)
    }

    val runButton: HtmlButtonElement =
      HtmlButtonElement.withTextLabel("basic/runProgram", _ => execute())

    val exp = TurtleGraphic.TurtleLineBasedProgram(List(
      TurtleGraphic.Line[Double](Point[Double](0, 0), Point[Double](100, 0)),
      TurtleGraphic.Line[Double](Point[Double](100, 0), Point[Double](100, 100))
    ))
    val renderingSignal: Signal[Element] = editor.state.signal.map(curState => {
      val curCommands = curState.toBeExpressionState.deriveTurtleCommands
      TurtleJsxGraphRenderer.render(curCommands, exp)
    })

    val interactivePreview = ElementCard(
      LanguageMapContentId("basic/gradingPreviewProgram"),
      renderingSignal
    )

    div(
      cls := "be-program-snap-fullscreen__turtle",
      onUnmountCallback { _ => stopRun() },
      h2(
        cls := "be-program-snap-fullscreen__turtle-title",
        text <-- laminarHelper.plaintextStringSignal(LanguageMapContentId("basic/turtleOutput"))
      ),
      runButton.getDomElement(),
      div(
        cls := "prog-ex-stage-output",
        canvasTag(
          cls := "be-program-snap-fullscreen__turtle-stage",
          aria.label := "Turtle stage",
          onMountCallback { ctx =>
            stageCanvas = Some(ctx.thisNode.ref.asInstanceOf[dom.HTMLCanvasElement])
          },
          onUnmountCallback { _ =>
            stageCanvas = None
          }
        )
      ),
      interactivePreview.getDomElement()
    )
  }
}
