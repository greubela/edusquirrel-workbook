package it.evadid.homepage.webElements.editor.code.SnapRenderer

import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.BeProgram
import it.evadid.vm.code.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.others.BeStartProgram
import org.scalajs.dom.html
import org.scalajs.dom.html.Canvas

/** Renders a [[BeProgram]] with Snap!/Morphic primitives.
  *
  * This renderer intentionally depends only on the core VM model and the
  * Snap! facades in this package. It does not use the legacy SVG BeBlock
  * rendering pipeline.
  */
trait BeProgramSnapRenderer() {

  def renderInto(program: BeProgram, canvas: Canvas): Unit

  def renderDefault(program: BeProgram, canvas: Canvas): Unit = {
    BeProgramSnapCustomRenderer().renderInto(program, canvas)

  }

}

