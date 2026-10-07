package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.airstream.ownership.ManualOwner
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.code.JavaFunctionBasedEditor
import it.evadid.homepage.webElements.editor.code.{CodeMirrorEditor, JavaEditorSession}
import it.evadid.vm.parsing.java.turtle.JavaTurtleResolution
import it.evadid.vm.simulation.java.{JavaTurtleEvaluation, JavaTurtleRuntime}
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.PyodideTurtleCommandRunner
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.workbook.elements.interactionElements.programming.*

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import java.util.concurrent.CancellationException
import scala.util.{Failure, Success, Try}

object EvaEditor {
  enum Tab(val label: String, val programmingLanguage: ProgrammingLanguage) {
    case Snap extends Tab("Snap!", SnapLanguage)
    case Python extends Tab("Python", AppLanguage.Python)
    case Java extends Tab("Java", AppLanguage.Java)
  }

  private[code] def tabFor(state: ProgrammingState): Tab = state match
    case _: ProgrammingStatePythonString => Tab.Python
    case _: ProgrammingStateJavaString => Tab.Java
    case _ => Tab.Snap
}

abstract class EvaEditor() extends HtmlAppElement with FullscreenLifecycle {

  def furtherDomElements(): List[Element]

  val state: Var[ProgrammingState]
  val config: EvaEditorConfig

  def onStateEdited: ProgrammingState => Unit = _ => ()
  def javaRunnerFactory: () => JavaEditorSession.Runner = JavaEditorSession.defaultRunner

  import EvaEditor.Tab

  private lazy val enabledTabs = config.enabledLanguages.distinct.map { language =>
    Tab.values.find(_.programmingLanguage == language).getOrElse(
      throw UnsupportedOperationException(s"ProgrammingLanguage '$language' not supported yet in EvaEditor!")
    )
  }
  private lazy val initialTab = EvaEditor.tabFor(state.now())
  private[code] lazy val activeTab = Var(
    if enabledTabs.contains(initialTab) then initialTab else enabledTabs.headOption.getOrElse(initialTab)
  )
  private[code] lazy val conversionError = Var(Option.empty[String])
  private lazy val viewAvailable = Var(
    enabledTabs.contains(initialTab) && !state.now().isInstanceOf[ProgrammingStateBeExpression]
  )
  private lazy val snapState = Var[ProgrammingState](state.now() match
    case snap: ProgrammingStateSnapXml => snap
    case floating: ProgrammingStateSnapXMLWithAdditionalFloatingObjects => floating.toSnapXml
    case _ => ProgrammingStateSnapXml.empty
  )
  private lazy val pythonState = Var(state.now() match
    case ProgrammingStatePythonString(code) => code
    case _ => ""
  )
  private lazy val javaState = Var[ProgrammingState](state.now() match
    case java: ProgrammingStateJavaString => java
    case _ => ProgrammingStateJavaString("")
  )
  private lazy val pythonRunner = new PyodideTurtleCommandRunner()
  private var mounted = false
  private var javaSession = Option.empty[JavaEditorSession]
  private class JavaRun {
    val owner = new ManualOwner
    var invalidated = false
    def invalidate(): Unit = { invalidated = true; owner.killSubscriptions() }
  }
  private var runningJava = Option.empty[JavaRun]

  private def releaseJavaSession(): Unit = {
    val pending = runningJava
    val discarded = javaSession
    runningJava = None
    javaSession = None
    pending.foreach(_.invalidate())
    discarded.foreach(_.release())
  }

  def stopJavaExecution(): Unit = {
    val pending = runningJava
    runningJava = None
    pending.foreach(_.invalidate())
    javaSession.foreach(_.stop())
  }

  private def runJava(source: ProgrammingStateJavaString): Future[List[TurtleCommand[Double]]] = {
    if runningJava.nonEmpty then return Future.failed(IllegalStateException("Java execution is already running."))
    Try(source.isClassProgram) match
      case Success(false) => return deriveCommands(source)
      case _ => ()
    val session = javaSession.getOrElse {
      val created = new JavaEditorSession(source, javaRunnerFactory)
      javaSession = Some(created)
      created
    }
    session.updateSource(source)
    val pending = new JavaRun
    runningJava = Some(pending)
    val original = state.now()
    state.signal.changes.foreach { next =>
      if next != original then releaseJavaSession()
    }(using pending.owner)
    Try(session.run()).fold(Future.failed, identity).map { execution =>
      if pending.invalidated then throw CancellationException("Java execution cancelled.")
      execution.status match {
        case JavaTurtleRuntime.Status.Completed => execution.commands.toList.map { command =>
          val name = command.command match
            case JavaTurtleResolution.TurtleCommand.Forward => "forward"
            case JavaTurtleResolution.TurtleCommand.TurnRight => "right"
          TurtleCommand[Double](name, List(command.value.toDouble))
        }
        case JavaTurtleRuntime.Status.Cancelled => throw CancellationException("Java execution cancelled.")
        case JavaTurtleRuntime.Status.LimitExceeded =>
          throw IllegalStateException("Your program reached its execution limit. Check its loops or recursion.")
        case JavaTurtleRuntime.Status.Failed(JavaTurtleRuntime.Failure.Evaluation(JavaTurtleEvaluation.Failure.DivisionByZero)) =>
          throw IllegalStateException("Your program tried to divide by zero.")
        case JavaTurtleRuntime.Status.Failed(_) => throw IllegalStateException("Your Java program could not finish.")
      }
    }.andThen { case _ =>
      pending.owner.killSubscriptions()
      if runningJava.exists(_ eq pending) then runningJava = None
    }
  }

  private def setViewAvailable(available: Boolean): Unit =
    if viewAvailable.now() != available then viewAvailable.set(available)

  private[code] def publish(tab: Tab, next: ProgrammingState): Unit = {
    if !enabledTabs.contains(tab) || activeTab.now() != tab || !viewAvailable.now() then return
    val retained = (state.now(), next) match
      case (floating: ProgrammingStateSnapXMLWithAdditionalFloatingObjects, snap: ProgrammingStateSnapXml) =>
        floating.copy(snapXml = snap.snapXml)
      case _ => next
    conversionError.set(None)
    if state.now() != retained then {
      state.set(retained)
      onStateEdited(retained)
    }
  }

  private def convert(state: ProgrammingState, tab: Tab): ProgrammingState = tab match
    case Tab.Snap => state.toSnapXml
    case Tab.Python => state match
      case java: ProgrammingStateJavaString if java.isClassProgram =>
        throw IllegalArgumentException("Full Java classes require the checked Java runner instead of the Python view.")
      case _ => state.toPython
    case Tab.Java => state.toJava

  private def show(next: ProgrammingState): Unit = next match
    case snap: ProgrammingStateSnapXml => snapState.set(snap)
    case floating: ProgrammingStateSnapXMLWithAdditionalFloatingObjects => snapState.set(floating.toSnapXml)
    case ProgrammingStatePythonString(code) => pythonState.set(code)
    case java: ProgrammingStateJavaString => javaState.set(java)
    case expression: ProgrammingStateBeExpression => snapState.set(expression.toSnapXml)

  private def receive(next: ProgrammingState): Unit = {
    if enabledTabs.isEmpty then {
      setViewAvailable(false)
      conversionError.set(Some("No programming languages are enabled. Your source is unchanged."))
      return
    }
    val matchingTab = EvaEditor.tabFor(next)
    val nextTab = if enabledTabs.contains(matchingTab) then matchingTab else activeTab.now()
    Try(show(if nextTab == matchingTab then next else convert(next, nextTab))) match
      case Success(_) =>
        if activeTab.now() != nextTab then activeTab.set(nextTab)
        setViewAvailable(true)
        conversionError.set(None)
      case Failure(_) =>
        setViewAvailable(false)
        if activeTab.now() != nextTab then activeTab.set(nextTab)
        conversionError.set(Some(s"This program cannot be displayed as ${nextTab.label} yet. Your source is unchanged."))
  }

  private lazy val snapEditor = Option.when(enabledTabs.contains(Tab.Snap))(
    SnapCodeEditor(snapState, config.snapConfig, next => publish(Tab.Snap, next))
  )
  private lazy val pythonEditor = Option.when(enabledTabs.contains(Tab.Python))(
    CodeMirrorEditor(pythonState, code => publish(Tab.Python, ProgrammingStatePythonString(code)), language = AppLanguage.Python)
  )
  private lazy val javaEditor = Option.when(enabledTabs.contains(Tab.Java))(
    new JavaFunctionBasedEditor(javaState, onStateEdited = next => publish(Tab.Java, next))
  )
  private lazy val snapElement = snapEditor.map(_.getDomElement())
  private lazy val pythonElement = pythonEditor.map(_.getDomElement())
  private lazy val javaElement = javaEditor.map(_.getDomElement())

  def currentState(): ProgrammingState = state.now()

  private def deriveCommands(source: ProgrammingState): Future[List[TurtleCommand[Double]]] =
    Try(source match
      case java: ProgrammingStateJavaString => java.toLegacyTurtleCommands
      case _ => source.toBeExpressionState.deriveTurtleCommands
    ).fold(Future.failed, Future.successful)

  def getCurrentTurtleCommands(): Future[List[TurtleCommand[Double]]] =
    if !mounted then state.now() match
      case snap: ProgrammingStateSnapXml => SnapCodeEditor.commandsFor(snap)
      case floating: ProgrammingStateSnapXMLWithAdditionalFloatingObjects => SnapCodeEditor.commandsFor(floating.toSnapXml)
      case ProgrammingStatePythonString(code) => pythonRunner.execute(code)
      case java: ProgrammingStateJavaString => runJava(java)
      case expression: ProgrammingStateBeExpression => deriveCommands(expression)
    else if !viewAvailable.now() then Future.failed(IllegalStateException("This draft cannot be run in the selected editor."))
    else activeTab.now() match
      case Tab.Snap => snapEditor.fold(Future.failed[List[TurtleCommand[Double]]](IllegalStateException("Snap is not enabled.")))(_.getCurrentTurtleCommands())
      case Tab.Python => pythonRunner.execute(pythonState.now())
      case Tab.Java => javaState.now() match
        case java: ProgrammingStateJavaString => runJava(java)
        case _ => Future.failed(IllegalStateException("No Java source is available."))

  private def closeActiveView(): Unit = activeTab.now() match
    case Tab.Snap => snapEditor.foreach(_.onFullscreenClose())
    case Tab.Java => javaEditor.foreach(_.onFullscreenClose())
    case Tab.Python => ()

  private[code] def select(tab: Tab): Unit = {
    if !enabledTabs.contains(tab) || activeTab.now() == tab then return
    val closedSnap = mounted && activeTab.now() == Tab.Snap
    if closedSnap then closeActiveView()
    Try(convert(state.now(), tab)) match
      case Success(next) =>
        releaseJavaSession()
        if mounted && !closedSnap then closeActiveView()
        show(next)
        conversionError.set(None)
        activeTab.set(tab)
        setViewAvailable(true)
        if mounted then onFullscreenOpen()
      case Failure(_) =>
        conversionError.set(Some(s"This draft cannot be converted to ${tab.label} yet. Your source is unchanged."))
        if closedSnap && viewAvailable.now() then onFullscreenOpen()
  }

  private def createTabButton(tab: Tab): Element =
    button(
      typ := "button",
      cls <-- activeTab.signal.map(active =>
        if active == tab then "eva-editor__tab eva-editor__tab--active" else "eva-editor__tab"
      ),
      aria.selected <-- activeTab.signal.map(_ == tab),
      tab.label,
      onClick --> (_ => select(tab))
    )

  private lazy val domElement: Element =
    div(
      cls := "eva-editor",
      state.signal --> receive,
      onMountCallback { _ => mounted = true },
      onUnmountCallback { _ => mounted = false; releaseJavaSession() },
      div(
        cls := "eva-editor__tabs",
        enabledTabs.map(createTabButton)
      ),
      child.maybe <-- conversionError.signal.map(_.map(message =>
        div(cls := "eva-editor__error", role := "alert", message)
      )),
      div(
        cls := "eva-editor__content",
        child <-- activeTab.signal.combineWith(viewAvailable.signal).map { (tab, available) =>
          if !available then div("This draft is not available in the selected editor.")
          else tab match
            case Tab.Snap => snapElement.getOrElse(div("Snap is not enabled."))
            case Tab.Python => pythonElement.getOrElse(div("Python is not enabled."))
            case Tab.Java => javaElement.getOrElse(div("Java is not enabled."))
        }
      ),
      furtherDomElements()
    )

  override def getDomElement(): Element = domElement

  override def onFullscreenOpen(): Unit =
    if viewAvailable.now() then activeTab.now() match
      case Tab.Snap => snapEditor.foreach(_.onFullscreenOpen())
      case Tab.Java => javaEditor.foreach(_.onFullscreenOpen())
      case Tab.Python => ()

  override def onFullscreenClose(): Unit = {
    releaseJavaSession()
    closeActiveView()
  }

  override def dismissOnOutsideClick: Boolean = false
}
