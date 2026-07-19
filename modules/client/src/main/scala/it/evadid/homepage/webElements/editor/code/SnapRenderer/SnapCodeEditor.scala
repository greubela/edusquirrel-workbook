package it.evadid.homepage.webElements.editor.code.SnapRenderer

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.SnapRenderer.SnapCodeEditor.SnapCodeEditorImpl
import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.TurtleFileSubmission
import it.evadid.vm.BeProgram
import org.scalajs.dom
import org.scalajs.dom.html.Canvas

case class SnapCodeEditor(program: Var[BeProgram], config: SnapCodeEditorConfig, impl: SnapCodeEditorImpl) extends HtmlAppElement {

  lazy val editorCanvas: L.Element = {
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
        aria.label := "Block program editor",
        widthAttr := config.CanvasWidth,
        heightAttr := config.CanvasHeight,
        display.block,
        // Construct WorldMorph from the canvas' own mount callback. Besides
        // avoiding an ambiguous descendant query, this guarantees that Snap
        // installs its listeners only after this exact canvas is connected.
        onMountCallback { ctx =>
          val canvas = ctx.thisNode.ref.asInstanceOf[dom.HTMLCanvasElement]
          impl.mount(ctx.owner)
          impl.onProjectXmlChanged(xml => program.set(TurtleFileSubmission.parseToBeProgram(xml)))
          impl.renderEditorInto(program.now(), canvas, config)
        }
      ),
      onUnmountCallback { _ =>
        // The interactive canvas exclusively owns the retained Morphic world.
        // Destroy it only when this editor host leaves the dialog.
        impl.destroy()
      }
    )
  }

  lazy val previewCanvas: L.Element = {
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
        display.block,
        onMountCallback { ctx =>
          val canvas = ctx.thisNode.ref.asInstanceOf[dom.HTMLCanvasElement]
          impl.renderPreviewInto(program.now(), canvas, config)
        }
      )
    )
  }

  override def getDomElement(): L.Element = {
    editorCanvas
  }

}

object SnapCodeEditor {

  def apply(program: Var[BeProgram]): SnapCodeEditor = {
    SnapCodeEditor(program, SnapCodeEditorConfig(), SnapCodeEditorImplDelegateToOriginal())
  }

  trait SnapCodeEditorImpl {

    /** Mount the complete interactive Snap editor and keep its Morphic world ticking. */
    def renderEditorInto(initProgram: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit

    /** Render only the scripts as a static, tightly-sized preview. */
    def renderPreviewInto(program: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit

    def mount(ctx: Owner): Unit

    /** Start driving the Morphic world. Snap controls only become responsive while cycles run. */
    def startWorldCycles(): Unit

    /** Pause Morphic updates without destroying the mounted editor. */
    def pauseWorldCycles(): Unit

    /** Register a listener for XML changes caused by edits in the mounted Snap project. */
    def onProjectXmlChanged(callback: String => Unit): Unit

    def destroy(): Unit
  }


}
