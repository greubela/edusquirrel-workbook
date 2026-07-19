package it.evadid.homepage.webElements.editor.code.SnapRenderer

import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.TurtleFileSubmission
import it.evadid.vm.BeProgram
import org.scalajs.dom.html.Canvas

import scala.scalajs.js

/**
 * Renders a BeProgram as Snap! canvas as is by transforming it to a xml and then rendering this xml in the editor.
 * Attention has to be paid to the fact that the scratch renderer assumes certain things about the canvas (i.e., its relative size in vw/vh) and makes some adjustments that are unwanted and have to be reverted in a dedicated method at the end of the drawing call (otherwise, the text looks very off).
 */

class BeProgramSnapOriginalRenderer extends BeProgramSnapRenderer {
  override def renderInto(program: BeProgram, canvas: Canvas): Unit =
    val canvasState = CanvasState.capture(canvas)
    val world = new WorldMorph(canvas, false)
    val editor = new IDEMorph(js.Dynamic.literal(
      noAutoFill = true,
      noCloud = true,
      noExitWarning = true
    ))

    editor.openIn(world)
    editor.loadProjectXML(TurtleFileSubmission.serializeFromBeExpression(program.fullProgram))

    // Snap's normal entry point is a full-page application and can overwrite
    // the host canvas' inline layout. Restore the embedding contract before
    // the final layout/draw cycle so that its font metrics use the real size.
    restoreEmbeddedCanvas(canvas, canvasState, world, editor)

  private def restoreEmbeddedCanvas(
      canvas: Canvas,
      state: CanvasState,
      world: WorldMorph,
      editor: IDEMorph
  ): Unit =
    canvas.style.cssText = state.inlineStyle
    canvas.width = state.width
    canvas.height = state.height

    world.setExtent(new SnapPoint(state.width.toDouble, state.height.toDouble))
    editor.setExtent(world.extent())
    editor.fixLayout()
    editor.fullChanged()
    world.changed()
    world.doOneCycle()
}

private final case class CanvasState(width: Int, height: Int, inlineStyle: String)

private object CanvasState:
  def capture(canvas: Canvas): CanvasState =
    CanvasState(canvas.width, canvas.height, canvas.style.cssText)
