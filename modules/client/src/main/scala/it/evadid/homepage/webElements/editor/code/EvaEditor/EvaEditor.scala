package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.code.JavaFunctionBasedEditor
import it.evadid.homepage.webElements.editor.code.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.{PyodideTurtleCommandRunner, SnapTurtleCommandExecution}
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

import scala.concurrent.Future
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
  def extensions: List[EvaEditorExtension] = Nil

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
  private lazy val snapExecution = new SnapTurtleCommandExecution(pythonRunner)
  private var mounted = false

  private def setViewAvailable(available: Boolean): Unit =
    if viewAvailable.now() != available then viewAvailable.set(available)

  private[code] def publish(tab: Tab, next: ProgrammingState): Unit = {
    if !enabledTabs.contains(tab) || activeTab.now() != tab || !viewAvailable.now() then return
    val retained = (state.now(), next) match
      case (previous: ProgrammingStateSnapXml, snap: ProgrammingStateSnapXml) =>
        previous.withProjectXml(snap.snapXml)
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

  private def currentViewState(): ProgrammingState =
    if !mounted then state.now()
    else if !viewAvailable.now() then throw IllegalStateException("This draft cannot be run in the selected editor.")
    else activeTab.now() match
      case Tab.Snap => snapState.now()
      case Tab.Python => ProgrammingStatePythonString(pythonState.now())
      case Tab.Java => javaState.now()

  private def deriveCommands(source: ProgrammingState): Future[List[TurtleCommand[Double]]] =
    Try(source match
      case java: ProgrammingStateJavaString => java.toLegacyTurtleCommands
      case _ => source.toBeExpressionState.deriveTurtleCommands
    ).fold(Future.failed, Future.successful)

  private def commandsFor(source: ProgrammingState): Future[List[TurtleCommand[Double]]] =
    extensions.iterator.flatMap(_.turtleCommands(source)).nextOption() match
      case Some(execute) => Try(execute()).fold(Future.failed, identity)
      case None => source match
        case snap: ProgrammingStateSnapXml => snapExecution.commandsFor(snap)
        case ProgrammingStatePythonString(code) => pythonRunner.execute(code)
        case _ => deriveCommands(source)

  private def captureSource(): ProgrammingState =
    if mounted && !viewAvailable.now() then throw IllegalStateException("This draft cannot be run in the selected editor.")
    else if mounted && activeTab.now() == Tab.Snap then
      snapEditor.getOrElse(throw IllegalStateException("Snap is not enabled.")).captureCurrentProject()
    else currentViewState()

  private def cancelExecution(): Unit = {
    pythonRunner.close()
    extensions.foreach(_.close())
  }

  private def stopExecution(): Unit = {
    pythonRunner.close()
    extensions.foreach(_.cancel())
  }

  protected lazy val turtleContext = EvaEditorExtension.Context(() => captureSource(), commandsFor, () => stopExecution())

  def getCurrentTurtleCommands(): Future[List[TurtleCommand[Double]]] =
    Try(captureSource()).fold(Future.failed, commandsFor)

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
        cancelExecution()
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
      aria.pressed <-- activeTab.signal.map(active => (active == tab).toString),
      aria.selected <-- activeTab.signal.map(_ == tab),
      tab.label,
      onClick --> (_ => select(tab)),
      disabled <-- state.signal.map(source => EvaEditor.tabFor(source) != tab && Try(convert(source, tab)).isFailure)
    )

  private lazy val domElement: Element =
    div(
      cls <-- activeTab.signal.combineWith(conversionError.signal).map { (tab, error) =>
        "eva-editor" + (if tab == Tab.Java then " eva-editor--java" else "") +
          (if error.nonEmpty then " eva-editor--error" else "")
      },
      state.signal --> receive,
      onMountCallback { _ => mounted = true },
      onUnmountCallback { _ => mounted = false; cancelExecution() },
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
      extensions.flatMap(_.panel(turtleContext)),
      furtherDomElements()
    )

  override def getDomElement(): Element = domElement

  override def onFullscreenOpen(): Unit =
    if viewAvailable.now() then activeTab.now() match
      case Tab.Snap => snapEditor.foreach(_.onFullscreenOpen())
      case Tab.Java => javaEditor.foreach(_.onFullscreenOpen())
      case Tab.Python => ()

  override def onFullscreenClose(): Unit = {
    cancelExecution()
    closeActiveView()
  }

  override def dismissOnOutsideClick: Boolean = false
}
