package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.{PyodideTurtleCommandRunner, SnapTurtleCommandExecution}
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.{SnapCodeEditorConfig, SnapCodeEditorImplDelegateToOriginal, SnapTurtleStagePanel}
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStateSnapXml
import org.scalajs.dom
import org.scalajs.dom.html.Canvas
import scala.concurrent.Future

case class SnapCodeEditor(
                           state: Var[ProgrammingState],
                           config: SnapCodeEditorConfig,
                           impl: SnapCodeEditorImpl,
                           onStateEdited: ProgrammingState => Unit = _ => ()
                         ) extends HtmlAppElement with FullscreenLifecycle {



  private def publishProgramFromSnapXml(xml: String): Unit = {
    val snap = ProgrammingStateSnapXml(xml).removeBloatFromXml
    val next = state.now() match {
      case floating: ProgrammingStateSnapXMLWithAdditionalFloatingObjects => floating.copy(snapXml = snap.snapXml)
      case _ => snap
    }
    // Mark the retained project before publishing: the Var observers run as
    // part of this update and must not reload Snap's own serialized project.
    impl.acknowledgeProgramFromEditor(snap)
    if state.now() != next then
      state.set(next)
      onStateEdited(next)
  }

  private[SnapEditor] def mountEditorInto(canvas: Canvas, owner: Owner): Unit = {
    impl.mount(owner)
    impl.setOnProjectXmlChangedListener(publishProgramFromSnapXml)
    impl.renderEditorInto(state.now().toSnapXml, canvas, config)
    // Mount once. Later updates only synchronize the retained project;
    // acknowledgeProgramFromEditor makes Snap-originated echoes no-ops.
    state.signal.changes.map(_.toSnapXml).distinct.foreach(impl.loadProgramIfChanged)(using owner)
  }

  lazy val editorCanvas: Element = {
    canvasTag(
      cls := "be-program-snap-renderer__canvas",
      aria.label := "Block program editor",
      widthAttr := config.visuals.CanvasWidth,
      heightAttr := config.visuals.CanvasHeight,
      // Construct WorldMorph from the canvas' own mount callback. Besides
      // avoiding an ambiguous descendant query, this guarantees that Snap
      // installs its listeners only after this exact canvas is connected.
      onMountCallback { ctx =>
        val canvas = ctx.thisNode.ref.asInstanceOf[dom.HTMLCanvasElement]
        mountEditorInto(canvas, ctx.owner)
      }
    )
  }

  lazy val editorDom: L.Element = {
    div(
      cls := "code-editor be-program-snap-renderer be-program-snap-renderer--editor",
      editorCanvas,
      onUnmountCallback { _ =>
        impl.flushPendingProjectChanges()
        impl.pauseWorldCycles()
      }
    )
  }

  lazy val domElement: L.Element =
    div(
      cls := "code-editor-container be-program-snap-fullscreen",
      editorDom
      //SnapTurtleStagePanel.chrome(this, flushPending = () => impl.flushPendingProjectChanges(), runOnStage = canvas => impl.runGreenFlagOnStage(canvas), stopRun = () => impl.stopGreenFlagOnStage()      )
    )

  override def getDomElement(): L.Element = domElement

  def captureCurrentProject(): ProgrammingStateSnapXml = {
    impl.flushPendingProjectChanges()
    impl.currentProjectXml().map(ProgrammingStateSnapXml(_)).getOrElse(state.now().toSnapXml)
  }

  def getCurrentTurtleCommands(): Future[List[TurtleCommand[Double]]] =
    SnapCodeEditor.commandsFor(captureCurrentProject())

  override def onFullscreenOpen(): Unit =
    impl.loadProgramIfChanged(state.now().toSnapXml)
    impl.fitEditorToContainer()
    impl.startWorldCycles()

  override def onFullscreenClose(): Unit =
    // Poll is paused with the world; flush so the last edits reach the Var/sync.
    impl.flushPendingProjectChanges()
    impl.pauseWorldCycles()


  override def dismissOnOutsideClick: Boolean = false
}


object SnapCodeEditor {

  private lazy val commandExecution = new SnapTurtleCommandExecution(new PyodideTurtleCommandRunner())

  private[code] def commandsFor(state: ProgrammingStateSnapXml): Future[List[TurtleCommand[Double]]] =
    commandExecution.commandsFor(state)

  def apply(state: Var[ProgrammingState], config: SnapCodeEditorConfig, onStateEdited: ProgrammingState => Unit): SnapCodeEditor =
    SnapCodeEditor(state, config, SnapCodeEditorImplDelegateToOriginal(), onStateEdited)

  def apply(state: Var[ProgrammingState], config: SnapCodeEditorConfig): SnapCodeEditor =
    SnapCodeEditor(state, config, SnapCodeEditorImplDelegateToOriginal())

  def apply(state: Var[ProgrammingState]): SnapCodeEditor =
    SnapCodeEditor(state, SnapCodeEditorConfig.Testing, SnapCodeEditorImplDelegateToOriginal())


}
