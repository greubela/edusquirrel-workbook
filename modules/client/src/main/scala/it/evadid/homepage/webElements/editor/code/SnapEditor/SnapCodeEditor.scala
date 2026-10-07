package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor.*
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.{PyodideTurtleCommandRunner, SnapTurtleCommandExecution}
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.{SnapCodeEditorConfig, SnapCodeEditorImplDelegateToOriginal, SnapPythonPopup, SnapTurtleStagePanel}
import it.evadid.workbook.elements.interactionElements.programming.*
import org.scalajs.dom
import org.scalajs.dom.IDBCursorDirection.next
import org.scalajs.dom.html.Canvas

import scala.concurrent.Future

case class SnapCodeEditor(
                           state: Var[ProgrammingState],
                           config: SnapCodeEditorConfig,
                           impl: SnapCodeEditorImpl
                         ) extends HtmlAppElement with FullscreenLifecycle {

  def removeAllLibraries(includeDefaultLibraries: Boolean = false): Unit =
    impl.removeAllLibraries(includeDefaultLibraries)

  private def reRenderCanvas(currentState: ProgrammingStateSnapXml, canvas: Canvas): Unit = {
    impl.loadProgramIfChanged(currentState)
    impl.renderEditorInto(currentState, canvas, config)
  }

  lazy val editorCanvas: Element = {
    canvasTag(
      cls := "be-program-snap-renderer__canvas",
      aria.label := "Block program editor",
      widthAttr := config.visuals.CanvasWidth,
      heightAttr := config.visuals.CanvasHeight,
      display.block,
      width := "100%",
      height := "100%",
      // Construct WorldMorph from the canvas' own mount callback. Besides
      // avoiding an ambiguous descendant query, this guarantees that Snap
      // installs its listeners only after this exact canvas is connected.
      onMountCallback { ctx =>
        val canvas = ctx.thisNode.ref.asInstanceOf[dom.HTMLCanvasElement]
        impl.mount(ctx.owner)
        impl.setOnProjectXmlChangedListener(newCode => state.set(ProgrammingStateSnapXml(newCode)))
        reRenderCanvas(state.now().toSnapXml, canvas)
        state.signal.foreach(newState => reRenderCanvas(newState.toSnapXml, canvas))(using ctx.owner)
      }
    )
  }

  lazy val editorDom: L.Element = {
    div(
      cls := "be-program-snap-renderer be-program-snap-renderer--editor",
      position.relative,
      overflow.hidden,
      border := "1px solid #d0d7de",
      borderRadius := "10px",
      backgroundColor := config.visuals.ColorWorkspace,
      width := "100%",
      height := "100%",
      minHeight := "0",
      flexGrow := "1",
      flexShrink := "1",
      flexBasis := "auto",
      boxSizing.borderBox,
      editorCanvas,
      onUnmountCallback { _ =>
        impl.flushPendingProjectChanges()
        impl.pauseWorldCycles()
      }
    )
  }

  lazy val domElement: L.Element =
    div(
      cls := "be-program-snap-fullscreen",
      editorCanvas,
      SnapTurtleStagePanel.chrome(
        this,
        flushPending = () => impl.flushPendingProjectChanges(),
        runOnStage = canvas => impl.runGreenFlagOnStage(canvas),
        stopRun = () => impl.stopGreenFlagOnStage()
      )
    )

  override def getDomElement(): L.Element = domElement

  override def onFullscreenOpen(): Unit =
    impl.forceLoadProgram(state.now().toSnapXml)
    impl.fitEditorToContainer()
    impl.startWorldCycles()

  override def onFullscreenClose(): Unit =
    // Poll is paused with the world; flush so the last edits reach the Var/sync.
    impl.flushPendingProjectChanges()
    impl.pauseWorldCycles()


  override def dismissOnOutsideClick: Boolean = false
}


object SnapCodeEditor {

  def apply(state: Var[ProgrammingState], config: SnapCodeEditorConfig): SnapCodeEditor =
    SnapCodeEditor(state, config, SnapCodeEditorImplDelegateToOriginal())

  def apply(state: Var[ProgrammingState]): SnapCodeEditor =
    SnapCodeEditor(state, SnapCodeEditorConfig.Testing, SnapCodeEditorImplDelegateToOriginal())


}

