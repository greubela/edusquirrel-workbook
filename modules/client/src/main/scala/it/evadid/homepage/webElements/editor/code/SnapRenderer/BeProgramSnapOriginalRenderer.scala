package it.evadid.homepage.webElements.editor.code.SnapRenderer

import com.raquo.airstream.ownership.Owner
import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.TurtleFileSubmission
import it.evadid.vm.BeProgram
import org.scalajs.dom.html.Canvas

import scala.scalajs.js

/**
 * Renders a BeProgram as Snap! canvas as is by transforming it to a xml and then rendering this xml in the editor.
 * Attention has to be paid to the fact that the scratch renderer assumes certain things about the canvas (i.e., its relative size in vw/vh) and makes some adjustments that are unwanted and have to be reverted in a dedicated method at the end of the drawing call (otherwise, the text looks very off).
 */

class BeProgramSnapOriginalRenderer() extends BeProgramSnapRenderer {

  private final case class CanvasState(width: Int, height: Int, inlineStyle: String)

  private object CanvasState:
    def capture(canvas: Canvas): CanvasState =
      CanvasState(canvas.width, canvas.height, canvas.style.cssText)

  override def renderInto(program: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit =
    val canvasState = CanvasState.capture(canvas)
    val world = new WorldMorph(canvas, false)
    val editor = new IDEMorph(js.Dynamic.literal(
      noAutoFill = true,
      noCloud = true,
      noExitWarning = true
    ))

    editor.openIn(world)
    // loadProjectXML delegates to openProjectString, which spreads loading over
    // IDE_Morph.nextSteps. A fixed number of world cycles is not a reliable way
    // to wait for that queue and used to leave this preview showing the canvas
    // background only. The original Snap code uses rawOpenProjectString for the
    // synchronous part of that operation, so use it before doing final layout.
    editor.rawOpenProjectString(TurtleFileSubmission.serializeFromBeExpression(program.fullProgram))

    // Snap's normal entry point is a full-page application and can overwrite
    // the host canvas' inline layout. Restore the embedding contract before
    // the final layout/draw cycle so that its font metrics use the real size.
    restoreEmbeddedCanvas(canvas, canvasState, world, editor)

    world.doOneCycle()
    world.doOneCycle()
    world.doOneCycle()
    CanvasVisibility.warnIfUnexpectedlyEmpty(this, program, canvas)

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

  override def mount(ctx: Owner): Unit = {}

  override def destroy(): Unit = {}
}
