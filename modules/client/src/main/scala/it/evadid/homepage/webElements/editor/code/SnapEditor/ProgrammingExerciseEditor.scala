package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.core.datastructures.vectorShapes.svg.BeExpressionToTurtleCommands
import it.evadid.vm.BeProgram
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.CodeMirrorEditor
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingEditorKind.{Python as PythonEditor, Snap}
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExerciseState.{PythonSource, SnapXml}
import it.evadid.workbook.elements.interactionElements.programming.{
  ProgrammingEditorKind,
  ProgrammingExerciseState,
  SnapProjectXml
}
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}

/**
 * Fullscreen programming editor. Snap and Python each keep their own stored
 * format; conversion runs only when the student switches.
 */
class ProgrammingExerciseEditor(
    state: Var[ProgrammingExerciseState],
    config: SnapCodeEditorConfig,
    allowedEditors: List[ProgrammingEditorKind],
    referencePython: Option[String] = None,
    onStateEdited: ProgrammingExerciseState => Unit
) extends HtmlAppElement with FullscreenLifecycle {

  private val snapVar: Var[SnapXml] = Var(initialSnap(state.now()))
  private val pythonText: Var[String] = Var(initialPython(state.now()))
  private val warning: Var[Option[String]] = Var(None)
  private val pythonRunMessage: Var[Option[String]] = Var(None)
  private var pythonStage: Option[dom.HTMLCanvasElement] = None
  private var stopPythonPlayback: () => Unit = () => ()
  /** False while Python is showing, so a background Snap flush cannot replace it. */
  private var acceptSnapEdits: Boolean = state.now().editor == Snap
  private var persistTimer = 0
  private var fullscreenOpen = false

  private val snapEditor: SnapCodeEditor =
    SnapCodeEditor(snapVar, config, onStateEdited = onSnapEdited, belowStage = targetPicture())

  private val pythonCode: CodeMirrorEditor =
    CodeMirrorEditor(pythonText, onUserInput = schedulePythonPersist, language = Python)

  private def initialSnap(current: ProgrammingExerciseState): SnapXml = current match
    case snap: SnapXml => snap
    case PythonSource(_, Some(xml)) => SnapXml(xml)
    case PythonSource(_, None) => SnapXml(SnapProjectXml.empty)

  private def initialPython(current: ProgrammingExerciseState): String = current match
    case PythonSource(source, _) => source
    case _: SnapXml => ""

  private def onSnapEdited(next: SnapXml): Unit =
    if acceptSnapEdits then onStateEdited(next)

  private def schedulePythonPersist(source: String): Unit =
    dom.window.clearTimeout(persistTimer)
    persistTimer = dom.window.setTimeout(() => persistPython(source), 300.0)

  private def flushPython(): Unit =
    dom.window.clearTimeout(persistTimer)
    persistTimer = 0
    if state.now().editor == PythonEditor then persistPython(pythonText.now())

  /** Keeps snapBase. A source that did not change does not write. */
  private def persistPython(source: String): Unit =
    val base = state.now() match
      case PythonSource(_, snapBase) => snapBase
      case snap: SnapXml => Some(snap.xml)
    val next = PythonSource(source, base)
    if state.now().fingerprint != next.fingerprint then onStateEdited(next)

  private def bind(owner: Owner): Unit =
    state.signal.changes.foreach {
      case snap: SnapXml =>
        if snapVar.now().xml != snap.xml then snapVar.set(snap)
      case PythonSource(source, _) =>
        if pythonText.now() != source then pythonText.set(source)
    }(using owner)

  private def switchTo(kind: ProgrammingEditorKind): Unit =
    if state.now().editor == kind then return
    warning.set(None)
    kind match
      case Snap =>
        stopPythonPlayback()
        flushPython()
        state.now() match
          case python: PythonSource =>
            ProgrammingStateConversion.pythonToSnap(python) match
              case Left(message) =>
                warning.set(Some(message))
              case Right(snap) =>
                acceptSnapEdits = true
                onStateEdited(snap)
                if snapVar.now().xml != snap.xml then snapVar.set(snap)
                showSnap()
          case _: SnapXml =>
            acceptSnapEdits = true
            showSnap()
      case PythonEditor =>
        // Flush while Snap edits are still accepted, so the conversion sees the latest XML.
        acceptSnapEdits = true
        snapEditor.onFullscreenClose()
        state.now() match
          case snap: SnapXml =>
            ProgrammingStateConversion.snapToPython(snap) match
              case Left(message) =>
                warning.set(Some(message))
                if fullscreenOpen then showSnap()
              case Right(python) =>
                acceptSnapEdits = false
                if pythonText.now() != python.source then pythonText.set(python.source)
                onStateEdited(python)
          case _: PythonSource =>
            acceptSnapEdits = false

  private def showSnap(): Unit =
    if fullscreenOpen then
      snapEditor.onFullscreenOpen()
      dom.window.requestAnimationFrame((_: Double) => snapEditor.refit())

  /** Workbook card: Snap preview, or the Python source read-only. */
  val preview: L.Element = div(
    cls := "programming-preview",
    div(
      cls.toggle("is-hidden") <-- state.signal.map(_.editor != Snap),
      snapEditor.previewCanvas
    ),
    pre(
      cls := "prog-ex-python-preview",
      cls.toggle("is-hidden") <-- state.signal.map(_.editor != PythonEditor),
      child.text <-- state.signal.map {
        case PythonSource(source, _) => source
        case _: SnapXml => ""
      }
    )
  )

  def getCurrentTurtleCommands(): Future[List[TurtleCommand[Double]]] =
    state.now() match
      case _: SnapXml =>
        snapEditor.getCurrentTurtleCommands()
      case _: PythonSource =>
        flushPython()
        SnapCodeEditor.commandsFor(state.now())

  override def getDomElement(): L.Element =
    div(
      cls := "programming-editor",
      onMountCallback { ctx =>
        bind(ctx.owner)
        if !acceptSnapEdits then
          dom.window.requestAnimationFrame { (_: Double) =>
            if !acceptSnapEdits then snapEditor.onFullscreenClose()
          }
      },
      switcher,
      child <-- warning.signal.map {
        case Some(message) if message.nonEmpty =>
          div(cls := "snap-python-popup__warning programming-editor__warning", message)
        case _ =>
          emptyNode
      },
      div(
        cls := "programming-editor__body",
        div(
          cls := "programming-editor__pane",
          cls.toggle("is-hidden") <-- state.signal.map(_.editor != Snap),
          snapEditor.getDomElement()
        ),
        div(
          cls := "programming-editor__pane programming-editor__python",
          cls.toggle("is-hidden") <-- state.signal.map(_.editor != PythonEditor),
          div(
            cls := "programming-editor__python-main",
            SnapPythonPopup.supportedFunctions,
            div(
              cls := "snap-python-popup__editor programming-editor__code",
              pythonCode.getDomElement()
            )
          ),
          pythonStagePanel
        )
      )
    )

  private def runPython(): Unit =
    stopPythonPlayback()
    flushPython()
    pythonRunMessage.set(None)
    SnapCodeEditor.commandsFor(state.now()).onComplete {
      case Success(commands) =>
        pythonStage.foreach(PythonTurtleCanvas.paint(_, commands))
      case Failure(error) =>
        val message = Option(error.getMessage).filter(_.nonEmpty).getOrElse("Could not run the program.")
        pythonRunMessage.set(Some(message))
    }

  private def pythonStagePanel: L.Element = {
    val runButton = HtmlButtonElement.withTextLabel("basic/runProgram", _ => runPython())
    div(
      cls := "be-program-snap-fullscreen__turtle",
      h2(cls := "be-program-snap-fullscreen__turtle-title", "Turtle"),
      runButton.getDomElement(),
      child <-- pythonRunMessage.signal.map {
        case Some(message) =>
          div(cls := "snap-python-popup__warning programming-editor__warning", message)
        case None =>
          emptyNode
      },
      canvasTag(
        cls := "programming-editor__python-canvas be-program-snap-fullscreen__turtle-stage",
        aria.label := "Turtle drawing",
        widthAttr := PythonTurtleCanvas.Width,
        heightAttr := PythonTurtleCanvas.Height,
        onMountCallback { ctx =>
          val canvas = ctx.thisNode.ref
          pythonStage = Some(canvas)
          PythonTurtleCanvas.paint(canvas, Nil)
        },
        onUnmountCallback { _ =>
          stopPythonPlayback()
          pythonStage = None
        }
      ),
      targetPicture()
    )
  }

  /** Static picture of the exercise target, when the task defines one. */
  private def targetPicture(): L.Node = referencePython.filter(_.trim.nonEmpty) match
    case None =>
      emptyNode
    case Some(source) =>
      div(
        cls := "programming-editor__target",
        h3(cls := "be-program-snap-fullscreen__turtle-title", "Target"),
        canvasTag(
          cls := "programming-editor__python-canvas be-program-snap-fullscreen__turtle-stage",
          aria.label := "Target drawing",
          widthAttr := PythonTurtleCanvas.Width,
          heightAttr := PythonTurtleCanvas.Height,
          onMountCallback { ctx =>
            Try {
              val program = BeProgram.fromPythonString(source)
              PythonTurtleCanvas.paint(ctx.thisNode.ref, BeExpressionToTurtleCommands.toPathBuilder(program.fullProgram))
            }
          }
        )
      )

  private def switcher: L.Node =
    if allowedEditors.size <= 1 then emptyNode
    else
      val buttons = allowedEditors.map { kind =>
        button(
          typ := "button",
          cls := "snap-python-button",
          cls.toggle("is-active") <-- state.signal.map(_.editor == kind),
          labelOf(kind),
          onClick --> { ev =>
            ev.stopPropagation()
            switchTo(kind)
          }
        )
      }
      div(cls := "programming-editor__switcher").amend(buttons*)

  private def labelOf(kind: ProgrammingEditorKind): String = kind match
    case Snap => "Blocks"
    case PythonEditor => "Python"

  override def onFullscreenOpen(): Unit =
    fullscreenOpen = true
    if state.now().editor == Snap then snapEditor.onFullscreenOpen()
    else
      acceptSnapEdits = false
      dom.window.requestAnimationFrame { (_: Double) =>
        if !acceptSnapEdits then snapEditor.onFullscreenClose()
      }

  override def onFullscreenClose(): Unit =
    fullscreenOpen = false
    stopPythonPlayback()
    flushPython()
    if state.now().editor != Snap then acceptSnapEdits = false
    snapEditor.onFullscreenClose()

  override def dismissOnOutsideClick: Boolean = false
}
