package it.evadid.homepage.webElements.editor.code.SnapRenderer

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.vm.BeProgram
import org.scalajs.dom

case class SnapCodeEditor(program: Var[BeProgram], config: SnapCodeEditorConfig, rendererFactory: () => BeProgramSnapRenderer) extends HtmlAppElement {

  private var curRenderer: Option[BeProgramSnapRenderer] = None

  override def getDomElement(): L.Element = {
    div(
      cls := "be-program-snap-renderer",
      position.relative,
      overflow.hidden,
      border := "1px solid #d0d7de",
      borderRadius := "10px",
      backgroundColor := config.ColorWorkspace,
      width := "fit-content",
      maxWidth := "100%",
      canvasTag(
        cls := "be-program-snap-renderer__canvas",
        aria.label := "Block program preview",
        widthAttr := config.CanvasWidth,
        heightAttr := config.CanvasHeight,
        display.block
      ),
      onMountCallback { ctx =>
        val host = ctx.thisNode.ref
        val canvas = host.querySelector("canvas").asInstanceOf[dom.HTMLCanvasElement]
        curRenderer = Some(rendererFactory())
        curRenderer.foreach(_.mount(ctx.owner))
        curRenderer.foreach(_.renderPreviewInto(program.now(), canvas, config))
      },
      onUnmountCallback { _ =>
        curRenderer.foreach(_.destroy())
        curRenderer = None
      }
    )
  }
}
