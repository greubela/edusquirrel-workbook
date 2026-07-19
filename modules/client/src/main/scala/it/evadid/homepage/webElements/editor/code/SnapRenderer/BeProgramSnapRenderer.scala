package it.evadid.homepage.webElements.editor.code.SnapRenderer

import com.raquo.airstream.ownership.Owner
import it.evadid.vm.BeProgram
import org.scalajs.dom.html.Canvas

/** Renders a [[BeProgram]] with Snap!/Morphic primitives.
 *
 * This renderer intentionally depends only on the core VM model and the
 * Snap! facades in this package. It does not use the legacy SVG BeBlock
 * rendering pipeline.
 *
 * Rendering priorities, in order:
 *   1. Render with Snap's original Morphic shapes and layout, without distortion.
 *   2. If original rendering fails, leave the canvas empty.
 *   3. Never substitute approximate or fallback shapes merely to make the canvas
 *      non-empty; visual fidelity to Snap is more important than showing output.
 * A low-color canvas may be logged as a rendering warning, but that diagnostic
 * must never alter or replace Snap's output.
 */
trait BeProgramSnapRenderer() {

  /** Mount the complete interactive Snap editor and keep its Morphic world ticking. */
  def renderEditorInto(initProgram: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit

  /** Render only the scripts as a static, tightly-sized preview. */
  def renderPreviewInto(program: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit

  def mount(ctx: Owner): Unit

  def destroy(): Unit
}

object BeProgramSnapRenderer {

  def defaultFactory(): BeProgramSnapRenderer = MountedProgramBlockRenderer()

}
