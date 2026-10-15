package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateSnapScalaModel
import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveSvgElement
import it.evadid.core.datastructures.language.AppLanguage.SnapLanguage
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorTab
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.{JavaTurtleSnapPrimitives, PyodideTurtleCommandRunner, SnapTurtleCommandExecution}
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.{SnapCodeEditorConfig, SnapCodeEditorImplDelegateToOriginal, SnapTurtleStagePanel}
import it.evadid.util.logging.Logger
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStateSnapXml
import org.scalajs.dom
import org.scalajs.dom.SVGSVGElement
import org.scalajs.dom.html.Canvas

import scala.concurrent.Future

case class SnapCodeEditor(
                           logger: Logger,
                           state: Var[ProgrammingState],
                           config: SnapCodeEditorConfig,
                           impl: SnapCodeEditorImpl,
                           onStateEdited: ProgrammingState => Unit = _ => ()
                         ) extends HtmlAppElement with FullscreenLifecycle {


  private def publishProgramFromSnapXml(xml: String): Unit = {
    val next: ProgrammingState = state.now() match {
      case snap: ProgrammingStateSnapXml => snap.withProjectXml(xml).removeBloatFromXml
      case snap: ProgrammingStateSnapScalaModel =>
        snap.toSnapXml.withProjectXml(xml).removeBloatFromXml.toSnapScalaModel.fold(
          message => throw IllegalArgumentException(message), identity)
      case _ => ProgrammingStateSnapXml(xml).removeBloatFromXml
    }
    // Mark the retained project before publishing: the Var observers run as
    // part of this update and must not reload Snap's own serialized project.
    impl.acknowledgeProgramFromEditor(next.toSnapXml)
    if state.now() != next then
      state.set(next)
      onStateEdited(next)
  }

  private[SnapEditor] def mountEditorInto(canvas: Canvas, owner: Owner): Unit = {
    JavaTurtleSnapPrimitives.install()
    impl.mount(owner)
    impl.setOnProjectXmlChangedListener(publishProgramFromSnapXml)
    impl.renderEditorInto(state.now().toSnapXml, canvas, config)
    // Mount once. Later updates only synchronize the retained project;
    // acknowledgeProgramFromEditor makes Snap-originated echoes no-ops.
    state.signal.changes.map(_.toSnapXml).distinct.foreach(impl.loadProgramIfChanged)(using owner)
  }

  lazy val editorCanvas: Element = {
    var mountGeneration = 0
    canvasTag(
      cls := "be-program-snap-renderer__canvas",
      aria.label := "Block program editor",
      widthAttr := config.visuals.CanvasWidth,
      heightAttr := config.visuals.CanvasHeight,
      // Construct WorldMorph from the canvas' own mount callback. Besides
      // avoiding an ambiguous descendant query, this guarantees that Snap
      // installs its listeners only after this exact canvas is connected.
      onMountCallback { ctx =>
        mountGeneration += 1
        val generation = mountGeneration
        val canvas = ctx.thisNode.ref.asInstanceOf[dom.HTMLCanvasElement]
        impl.whenReady(() => {
          if generation == mountGeneration then
            renderingError.set(None)
            mountEditorInto(canvas, ctx.owner)
        }, error => {
          if generation == mountGeneration then renderingError.set(Some(s"Block editor unavailable: ${error.getMessage}"))
        })
      },
      onUnmountCallback(_ => mountGeneration += 1)
    )
  }

  lazy val editorDom: L.Element = {
    div(
      cls := "code-editor be-program-snap-renderer be-program-snap-renderer--editor",
      child.maybe <-- renderingError.signal.map(_.map(message => p(role := "alert", message))),
      editorCanvas,
      onUnmountCallback { _ =>
        impl.flushPendingProjectChanges()
        impl.pauseWorldCycles()
      }
    )
  }

  private val renderingError = Var(Option.empty[String])

  lazy val domElement: L.Element =
    div(
      cls := "code-editor-container be-program-snap-fullscreen",
      editorDom
      //SnapTurtleStagePanel.chrome(this, flushPending = () => impl.flushPendingProjectChanges(), runOnStage = canvas => impl.runGreenFlagOnStage(canvas), stopRun = () => impl.stopGreenFlagOnStage()      )
    )

  override def getDomElement(): L.Element = domElement

  def captureCurrentProject(): ProgrammingStateSnapXml = {
    impl.flushPendingProjectChanges()
    val previous = state.now().toSnapXml
    impl.currentProjectXml().map(previous.withProjectXml).getOrElse(previous)
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

  def apply(logger: Logger, state: Var[ProgrammingState], config: SnapCodeEditorConfig, onStateEdited: ProgrammingState => Unit): SnapCodeEditor =
    SnapCodeEditor(logger, state, config, SnapCodeEditorImplDelegateToOriginal(), onStateEdited)

  def apply(logger: Logger,state: Var[ProgrammingState], config: SnapCodeEditorConfig): SnapCodeEditor =
    SnapCodeEditor(logger, state, config, SnapCodeEditorImplDelegateToOriginal())

  def apply(logger: Logger,state: Var[ProgrammingState]): SnapCodeEditor =
    SnapCodeEditor(logger, state, SnapCodeEditorConfig.Testing, SnapCodeEditorImplDelegateToOriginal())


  def forEvaTab(logger: Logger, config: SnapCodeEditorConfig, initState: ProgrammingState): EvaEditorTab[ProgrammingState] = {

    val internal: Var[ProgrammingState] = Var[ProgrammingState](initState.toSnapXml)
    val editor = SnapCodeEditor(logger, internal, config)

    new EvaEditorTab[ProgrammingState]() {
      override val underlyingVar: Var[ProgrammingState] = internal

      override def convert(internal: ProgrammingState): ProgrammingState = internal

      override def getDomElement(): L.Element = editor.getDomElement()

      override def logger: Logger = logger

      override def label: String = "Snap Editor"

      override def icon: Option[ReactiveSvgElement[SVGSVGElement]] = None

      override def additionalCssForEvaEditor: String = ""
    }

  }

}
