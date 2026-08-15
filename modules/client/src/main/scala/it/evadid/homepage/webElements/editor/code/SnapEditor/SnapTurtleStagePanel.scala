package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
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
 */
object SnapTurtleStagePanel {

  /**
   * Right-hand turtle panel for the fullscreen Snap shell.
   * @param flushPending call before run so Snap XML edits are published
   * @param runOnStage green-flag the live IDE stage and mirror frames onto the canvas
   * @param stopRun stop scripts / cancel mirroring (e.g. on unmount)
   */
  def chrome(
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
          display.block,
          maxWidth := "100%",
          height.auto,
          onMountCallback { ctx =>
            stageCanvas = Some(ctx.thisNode.ref.asInstanceOf[dom.HTMLCanvasElement])
          },
          onUnmountCallback { _ =>
            stageCanvas = None
          }
        )
      )
    )
  }
}
