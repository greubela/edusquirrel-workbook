package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor.SnapCodeEditorImpl
import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.TurtleFileSubmission
import it.evadid.vm.BeProgram
import org.scalajs.dom
import org.scalajs.dom.html.Canvas

case class SnapCodeEditor(program: Var[BeProgram], config: SnapCodeEditorConfig, impl: SnapCodeEditorImpl)
    extends HtmlAppElement with FullscreenLifecycle {

  /**
   * The interactive session is deliberately scoped to one DOM mount:
   *
   *   Unmounted --canvas mounts--> Mounted --canvas unmounts--> Unmounted
   *
   * A mount creates exactly one WorldMorph/IDE pair. An unmount destroys that
   * pair (and first persists its XML). Reopening the dialog mounts the same
   * Laminar element, but creates a fresh Snap session from `program`. Merely
   * opening/closing fullscreen only starts/stops cycles; it never constructs a
   * world. Keeping a detached world or forwarding input from a mirror canvas
   * would retain document listeners, focus state, pointer capture and popup
   * coordinates, making it both more complex and less reliable than recreation.
   */
  private enum EditorMount:
    case Unmounted
    case Mounted(canvas: dom.HTMLCanvasElement)

  private var editorMount: EditorMount = EditorMount.Unmounted
  private var fullscreenOpen = false

  lazy val editorCanvas: L.Element = {
    div(
      cls := "be-program-snap-renderer",
      position.relative,
      overflow.hidden,
      border := "1px solid #d0d7de",
      borderRadius := "10px",
      backgroundColor := config.visuals.ColorWorkspace,
      width := "fit-content",
      maxWidth := "100%",
      canvasTag(
        cls := "be-program-snap-renderer__canvas",
        aria.label := "Block program editor",
        widthAttr := config.visuals.CanvasWidth,
        heightAttr := config.visuals.CanvasHeight,
        display.block,
        // Construct WorldMorph from the canvas' own mount callback. Besides
        // avoiding an ambiguous descendant query, this guarantees that Snap
        // installs its listeners only after this exact canvas is connected.
        onMountCallback { ctx =>
          val canvas = ctx.thisNode.ref.asInstanceOf[dom.HTMLCanvasElement]
          editorMount match
            case EditorMount.Unmounted =>
              impl.setOnProjectXmlChangedListener(xml => program.set(TurtleFileSubmission.parseToBeProgram(xml)))
              impl.renderEditorInto(program.now(), canvas, config)
              editorMount = EditorMount.Mounted(canvas)
              if fullscreenOpen then impl.startWorldCycles()
            case EditorMount.Mounted(mountedCanvas) =>
              require(mountedCanvas eq canvas, "SnapCodeEditor cannot be mounted on two canvases at once")
        }
      ),
      onUnmountCallback { _ =>
        editorMount match
          case EditorMount.Mounted(_) =>
            impl.destroy()
            editorMount = EditorMount.Unmounted
          case EditorMount.Unmounted => ()
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
      backgroundColor := config.visuals.ColorWorkspace,
      width := "fit-content",
      maxWidth := "100%",
      canvasTag(
        cls := "be-program-snap-renderer__canvas",
        aria.label := "Block program preview",
        widthAttr := config.visuals.CanvasWidth,
        heightAttr := config.visuals.CanvasHeight,
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

  /** Drop custom tabs, optionally removing Snap's default library as well. */
  def removeAllLibraries(includeDefaultLibraries: Boolean = false): Unit =
    impl.removeAllLibraries(includeDefaultLibraries)

  override def onFullscreenOpen(): Unit =
    fullscreenOpen = true
    editorMount match
      case EditorMount.Mounted(_) => impl.startWorldCycles()
      case EditorMount.Unmounted => ()

  override def onFullscreenClose(): Unit =
    fullscreenOpen = false
    editorMount match
      case EditorMount.Mounted(_) => impl.pauseWorldCycles()
      case EditorMount.Unmounted => ()

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
