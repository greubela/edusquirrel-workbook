package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.state.Var
import com.raquo.airstream.ownership.ManualOwner
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.homepage.webElements.editor.code.SnapEditor.{SnapCodeEditor, SnapCodeEditorConfig}
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.PyodideTurtleCommandRunner
import it.evadid.workbook.elements.interactionElements.programming.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import java.util.concurrent.CancellationException
import scala.util.{Failure, Success, Try}

/** Full-screen programming editor that owns one polymorphic state and presents
  * a suitable editor for every supported representation.
  */
final class EvaEditor(
    val state: Var[ProgrammingState],
    snapConfig: SnapCodeEditorConfig,
    onStateEdited: ProgrammingState => Unit = _ => (),
    javaRunnerFactory: () => JavaEditorSession.Runner = () => JavaEditorSession.defaultRunner()
) extends HtmlAppElement with FullscreenLifecycle {

  private[code] val activeTab = Var(EvaEditor.tabFor(state.now()))
  private[code] val conversionError = Var(Option.empty[String])
  private val viewAvailable = Var(!state.now().isInstanceOf[ProgrammingStateBeExpression])
  private val snapState = Var(state.now() match
    case snap: ProgrammingStateSnapXml => snap
    case floating: ProgrammingStateSnapXMLWithAdditionalFloatingObjects => floating.toSnapXml
    case _ => ProgrammingExerciseState.empty
  )
  private val pythonState = Var(state.now() match
    case ProgrammingStatePythonString(code) => code
    case _ => ""
  )
  private val javaState = Var(state.now() match
    case java: ProgrammingStateJavaString => java
    case _ => ProgrammingStateJavaString("")
  )
  private lazy val pythonRunner = new PyodideTurtleCommandRunner()
  private var mounted = false

  private def setViewAvailable(available: Boolean): Unit =
    if viewAvailable.now() != available then viewAvailable.set(available)

  private[code] def publish(tab: EvaEditor.Tab, next: ProgrammingState): Unit =
    if activeTab.now() != tab || !viewAvailable.now() then return
    val retained = (state.now(), next) match
      case (floating: ProgrammingStateSnapXMLWithAdditionalFloatingObjects, snap: ProgrammingStateSnapXml) =>
        floating.copy(snapXml = snap.snapXml)
      case _ => next
    conversionError.set(None)
    state.set(retained)
    onStateEdited(retained)

  private def show(next: ProgrammingState): Unit = next match
    case snap: ProgrammingStateSnapXml => snapState.set(snap)
    case floating: ProgrammingStateSnapXMLWithAdditionalFloatingObjects => snapState.set(floating.toSnapXml)
    case ProgrammingStatePythonString(code) => pythonState.set(code)
    case java: ProgrammingStateJavaString => javaState.set(java)
    case expression: ProgrammingStateBeExpression => snapState.set(expression.toSnapXml)

  private def receive(next: ProgrammingState): Unit =
    val nextTab = EvaEditor.tabFor(next)
    // Re-emitting the selected tab replaces Laminar's child node. Avoid doing
    // that for edits in the current CodeMirror instance, or it loses focus.
    Try(show(next)) match
      case Success(_) =>
        if activeTab.now() != nextTab then activeTab.set(nextTab)
        setViewAvailable(true)
        conversionError.set(None)
      case Failure(_) =>
        setViewAvailable(false)
        if activeTab.now() != nextTab then activeTab.set(nextTab)
        conversionError.set(Some("This program cannot be displayed in Snap yet. Your source is unchanged."))

  private val snapEditor = SnapCodeEditor(
    snapState,
    snapConfig,
    onStateEdited = next => publish(EvaEditor.Tab.Snap, next)
  )
  private val pythonEditor = CodeMirrorEditor(
    pythonState,
    code => publish(EvaEditor.Tab.Python, ProgrammingStatePythonString(code)),
    language = AppLanguage.Python
  )
  private val javaEditor = new JavaEditor(
    javaState,
    next => publish(EvaEditor.Tab.Java, next),
    javaRunnerFactory
  )
  private lazy val snapElement = snapEditor.getDomElement()
  private lazy val pythonElement = pythonEditor.getDomElement()
  private lazy val javaElement = javaEditor.getDomElement()

  /** Small preview retained by the workbook card. */
  lazy val previewCanvas: Element =
    val available = Var(false)
    div(
      onMountCallback { ctx =>
        state.signal.foreach { next =>
          Try(next.toSnapXml) match
            case Success(snap) =>
              snapState.set(snap)
              if !available.now() then available.set(true)
            case Failure(_) =>
              if available.now() then available.set(false)
        }(using ctx.owner)
      },
      child <-- available.signal.map { ready =>
        if ready then snapEditor.previewCanvas
        else div("Preview unavailable for this draft.")
      }
    )

  /** The representation currently owned by the editor. All derived behavior starts here. */
  def currentState(): ProgrammingState = state.now()

  def getCurrentTurtleCommands(): Future[List[TurtleCommand[Double]]] =
    if !mounted then state.now() match
      case snap: ProgrammingStateSnapXml => SnapCodeEditor.commandsFor(snap)
      case floating: ProgrammingStateSnapXMLWithAdditionalFloatingObjects => SnapCodeEditor.commandsFor(floating.toSnapXml)
      case ProgrammingStatePythonString(code) => pythonRunner.execute(code)
      case java: ProgrammingStateJavaString => javaCommands(java)
      case expression: ProgrammingStateBeExpression =>
        Try(expression.toSnapXml).fold(Future.failed, SnapCodeEditor.commandsFor)
    else if !viewAvailable.now() then Future.failed(IllegalStateException("This draft cannot be run in the selected editor."))
    else activeTab.now() match
      case EvaEditor.Tab.Snap => snapEditor.getCurrentTurtleCommands()
      case EvaEditor.Tab.Python => pythonRunner.execute(pythonState.now())
      case EvaEditor.Tab.Java => javaCommands(javaState.now())

  private def javaCommands(source: ProgrammingStateJavaString): Future[List[TurtleCommand[Double]]] = {
    javaState.set(source)
    val original = ProgrammingState.fingerprint(state.now())
    val owner = new ManualOwner
    var changed = false
    state.signal.changes.foreach { next =>
      if ProgrammingState.fingerprint(next) != original then {
        changed = true
        next match
          case java: ProgrammingStateJavaString => javaState.set(java)
          case _ => javaEditor.stop()
      }
    }(using owner)
    javaEditor.getCurrentTurtleCommands().transform { result =>
      if changed || original != ProgrammingState.fingerprint(state.now()) then
        Failure(new CancellationException("Java source changed."))
      else result
    }.andThen { case _ => owner.killSubscriptions() }
  }

  private[code] def select(tab: EvaEditor.Tab): Unit =
    if activeTab.now() == tab then return
    if activeTab.now() == EvaEditor.Tab.Snap then snapEditor.onFullscreenClose()
    val converted = Try {
      val current = state.now()
      tab match
        case EvaEditor.Tab.Snap => current.toSnapXml
        case EvaEditor.Tab.Python => current.toPython
        case EvaEditor.Tab.Java => current.toJava
    }
    converted match
      case Success(next) =>
        if activeTab.now() == EvaEditor.Tab.Java then javaEditor.onFullscreenClose()
        show(next)
        conversionError.set(None)
        activeTab.set(tab)
        setViewAvailable(true)
      case Failure(_) =>
        conversionError.set(Some(s"This draft cannot be converted to ${tab.label} yet. Your source is unchanged."))
        if activeTab.now() == EvaEditor.Tab.Snap && viewAvailable.now() then snapEditor.onFullscreenOpen()

  override def getDomElement(): Element =
    div(
      cls := "eva-editor",
      state.signal --> receive,
      onMountCallback { _ => mounted = true },
      onUnmountCallback { _ => mounted = false },
      div(
        cls := "eva-editor__tabs",
        EvaEditor.Tab.values.map { tab =>
          button(
            typ := "button",
            cls <-- activeTab.signal.map(active =>
              if active == tab then "eva-editor__tab eva-editor__tab--active" else "eva-editor__tab"
            ),
            aria.selected <-- activeTab.signal.map(_ == tab),
            tab.label,
            onClick --> (_ => select(tab))
          )
        }
      ),
      child.maybe <-- conversionError.signal.map(_.map(message =>
        div(cls := "eva-editor__error", role := "alert", message)
      )),
      div(
        cls := "eva-editor__content",
        child <-- activeTab.signal.combineWith(viewAvailable.signal).map { (tab, available) =>
          if !available then div("This draft is not available in the selected editor.")
          else tab match
            case EvaEditor.Tab.Snap => snapElement
            case EvaEditor.Tab.Python => pythonElement
            case EvaEditor.Tab.Java => javaElement
        }
      )
    )

  override def onFullscreenOpen(): Unit =
    if activeTab.now() == EvaEditor.Tab.Snap && viewAvailable.now() then snapEditor.onFullscreenOpen()

  override def onFullscreenClose(): Unit = {
    snapEditor.onFullscreenClose()
    javaEditor.onFullscreenClose()
  }
  override def dismissOnOutsideClick: Boolean = false
}

object EvaEditor {
  enum Tab(val label: String) {
    case Snap extends Tab("Snap!")
    case Python extends Tab("Python")
    case Java extends Tab("Java")
  }

  private[code] def tabFor(state: ProgrammingState): Tab = state match
    case _: ProgrammingStatePythonString => Tab.Python
    case _: ProgrammingStateJavaString => Tab.Java
    case _ => Tab.Snap

}
