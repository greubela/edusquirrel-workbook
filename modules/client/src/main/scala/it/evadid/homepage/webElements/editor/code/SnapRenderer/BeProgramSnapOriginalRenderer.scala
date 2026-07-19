package it.evadid.homepage.webElements.editor.code.SnapRenderer

import it.evadid.vm.BeProgram
import org.scalajs.dom.html.Canvas


/**
 * Renders a BeProgram as Snap! canvas as is by transforming it to a xml and then rendering this xml in the editor.
 * Attention has to be paid to the fact that the scratch renderer assumes certain things about the canvas (i.e., its relative size in vw/vh) and makes some adjustments that are unwanted and have to be reverted in a dedicated method at the end of the drawing call (otherwise, the text looks very off).
 */

class BeProgramSnapOriginalRenderer extends BeProgramSnapRenderer {
  override def renderInto(program: BeProgram, canvas: Canvas): Unit = ???
}
