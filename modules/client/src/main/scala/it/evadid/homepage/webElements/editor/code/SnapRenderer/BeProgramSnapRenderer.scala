package it.evadid.homepage.webElements.editor.code.SnapRenderer

import com.raquo.airstream.ownership.Owner
import it.evadid.vm.BeProgram
import org.scalajs.dom.html.Canvas

/** Renders a [[BeProgram]] with Snap!/Morphic primitives.
 *
 * This renderer intentionally depends only on the core VM model and the
 * Snap! facades in this package. It does not use the legacy SVG BeBlock
 * rendering pipeline.
 */
trait BeProgramSnapRenderer() {

  def renderInto(program: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit

  def mount(ctx: Owner): Unit

  def destroy(): Unit
}

object BeProgramSnapRenderer {

  def defaultFactory(): BeProgramSnapRenderer = BeProgramSnapOriginalRenderer()

}

