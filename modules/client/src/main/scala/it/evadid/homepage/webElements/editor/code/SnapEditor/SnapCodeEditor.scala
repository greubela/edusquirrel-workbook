package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.State
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor.SnapCodeEditorImpl
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditorConfig.SnapCodeEditorConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.TurtleFileSubmission
import it.evadid.vm.BeProgram
import org.scalajs.dom
import org.scalajs.dom.html.Canvas


case class SnapCodeEditor(program: Var[BeProgram], config: Var[SnapCodeEditorConfig]) extends HtmlAppElement {

  case class ActiveEditorMount(canvas: dom.HTMLCanvasElement, editorImpl: SnapCodeEditorImpl)

  private val activeMount: State[Option[ActiveEditorMount]] = State(None)

  private def onMounted(canvas: dom.HTMLCanvasElement): Unit = {
    /*editorMount match
      case EditorMount.Unmounted =>
        impl.setOnProjectXmlChangedListener(xml => program.set(TurtleFileSubmission.parseToBeProgram(xml)))
        impl.renderEditorInto(program.now(), canvas, config)
        editorMount = EditorMount.Mounted(canvas)
        if fullscreenOpen then impl.startWorldCycles()
      case EditorMount.Mounted(mountedCanvas) =>
        require(mountedCanvas eq canvas, "SnapCodeEditor cannot be mounted on two canvases at once")*/
  }

  private def onUnmounted(canvas: dom.HTMLCanvasElement): Unit = {
    /*editorMount match
      case EditorMount.Mounted(_) =>
        impl.destroy()
        editorMount = EditorMount.Unmounted
      case EditorMount.Unmounted => ()*/
  }

  private def createCanvasAndEditor(curConfig: SnapCodeEditorConfig): L.Element = {
    div(
      cls := "snap-code-editor",
      canvasTag(
        aria.label := "Block program editor",
        widthAttr <-- config.signal.map(_.visuals.CanvasWidth),
        heightAttr <-- config.signal.map(_.visuals.CanvasHeight),
        onMountCallback { ctx => onMounted(ctx.thisNode.ref.asInstanceOf[dom.HTMLCanvasElement]) },
        onUnmountCallback { ctx => onUnmounted(ctx.ref) }
      ))
  }

}

object SnapCodeEditor {

  def apply(program: Var[BeProgram]): SnapCodeEditor = {
    SnapCodeEditor(program, SnapCodeEditorConfig.Testing, SnapCodeEditorImplDelegateToOriginal())
  }

  trait SnapCodeEditorImpl {

    /** Mount the complete interactive Snap editor and keep its Morphic world ticking. */
    def renderEditorInto(initProgram: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit

    /** Render only the scripts as a static, tightly-sized preview. */
    def renderPreviewInto(program: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit

    /** Start driving the Morphic world. Snap controls only become responsive while cycles run. */
    def startWorldCycles(): Unit

    /** Pause Morphic updates without destroying the mounted editor. */
    def pauseWorldCycles(): Unit

    /** Register a listener for XML changes caused by edits in the mounted Snap project. */
    def setOnProjectXmlChangedListener(callback: String => Unit): Unit

    /** Remove custom tabs and, when requested, Snap's standard library too. */
    def removeAllLibraries(includeDefaultLibraries: Boolean = false): Unit

    def destroy(): Unit
  }


}
