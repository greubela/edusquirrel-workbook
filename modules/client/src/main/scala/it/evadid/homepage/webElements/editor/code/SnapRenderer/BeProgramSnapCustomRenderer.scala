package it.evadid.homepage.webElements.editor.code.SnapRenderer

import com.raquo.airstream.ownership.Owner
import it.evadid.vm.BeProgram
import org.scalajs.dom.html.Canvas

class BeProgramSnapCustomRenderer extends BeProgramSnapRenderer:

  override def renderEditorInto(initProgram: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit =
    println("BeProgramSnapCustomRenderer.renderEditorInto is not implemented yet")

  override def renderPreviewInto(program: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit =
    println("BeProgramSnapCustomRenderer.renderPreviewInto is not implemented yet")

  override def mount(ctx: Owner): Unit = ()

  override def destroy(): Unit = ()
