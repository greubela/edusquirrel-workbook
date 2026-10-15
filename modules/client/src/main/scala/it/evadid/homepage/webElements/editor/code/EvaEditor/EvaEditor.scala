package it.evadid.homepage.webElements.editor.code.EvaEditor

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateSnapScalaModel
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.{PyodideTurtleCommandRunner, SnapTurtleCommandExecution}
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.util.logging.{Logger, LoggingLevel}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
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

  private val conversionLogger = new Logger {
    override def log(message: String, level: LoggingLevel): Unit = ()
    override def getOut(): String = ""
    override def getErr(): String = ""
  }

  private[code] final class ConversionCache(convert: (ProgrammingState, Tab) => Future[ProgrammingState]) {
    private var latest = Option.empty[(ProgrammingState, AsyncDataCache[Tab, Try[ProgrammingState]])]

    def apply(source: ProgrammingState, tab: Tab): Future[Try[ProgrammingState]] = {
      val cache = latest.filter(_._1 == source).map(_._2).getOrElse {
        val created = new AsyncDataCache[Tab, Try[ProgrammingState]](conversionLogger, retryFailedOnNextFetch = false) {
          override protected def executeLoading(tab: Tab)(ec: ExecutionContext): Future[Try[ProgrammingState]] =
            Try(convert(source, tab)).fold(Future.failed, identity)
              .map(value => Success(value): Try[ProgrammingState])(using ec)
              .recover { case error => Failure(error) }(using ec)
          override protected def formatInputForLogging(tab: Tab): String = tab.label
          override protected def formatOutputForLogging(result: Try[ProgrammingState]): String =
            if result.isSuccess then "available" else "unavailable"
        }
        latest = Some(source -> created)
        created
      }
      cache.loadAsFuture(tab)
    }

    def clear(): Unit = latest = None
  }
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
    case snap: ProgrammingStateSnapScalaModel => snap.toSnapXml
    case _ => ProgrammingStateSnapXml.empty
  )
  private lazy val pythonState = Var[ProgrammingState](state.now() match
    case python: ProgrammingStatePythonString => python
    case _ => ProgrammingStatePythonString("")
  )
  private lazy val javaState = Var[ProgrammingState](state.now() match
    case java: ProgrammingStateJavaString => java
    case _ => ProgrammingStateJavaString("")
  )
  private lazy val pythonRunner = new PyodideTurtleCommandRunner()
  private lazy val snapExecution = new SnapTurtleCommandExecution(pythonRunner)
  private var mounted = false
  private var viewOpen = true
  private var viewRequest = 0L
  private lazy val appearance = new EditorAppearance

  private def setViewAvailable(available: Boolean): Unit =
    if viewAvailable.now() != available then viewAvailable.set(available)

  private[code] def publish(tab: Tab, next: ProgrammingState): Unit = {
    if !enabledTabs.contains(tab) || activeTab.now() != tab || !viewAvailable.now() then return
    val retained = (state.now(), next) match
      case (previous: ProgrammingStateSnapXml, snap: ProgrammingStateSnapXml) =>
        previous.withProjectXml(snap.snapXml)
      case (previous: ProgrammingStateSnapScalaModel, snap: ProgrammingStateSnapXml) =>
        previous.toSnapXml.withProjectXml(snap.snapXml).toSnapScalaModel.fold(
          message => throw IllegalArgumentException(message), identity)
      case _ => next
    conversionError.set(None)
    if state.now() != retained then {
      viewRequest += 1
      state.set(retained)
      onStateEdited(retained)
    }
  }

  protected def convert(state: ProgrammingState, tab: Tab): Future[ProgrammingState] = Future.fromTry(Try(tab match
    case Tab.Snap => state.toSnapXml
    case Tab.Python => state.toPython
    case Tab.Java => state.toJava
  ))

  private lazy val conversions = new EvaEditor.ConversionCache(convert)

  private def show(next: ProgrammingState): Unit = next match
    case snap: ProgrammingStateSnapXml => snapState.set(snap)
    case snap: ProgrammingStateSnapScalaModel => snapState.set(snap.toSnapXml)
    case python: ProgrammingStatePythonString => pythonState.set(python)
    case java: ProgrammingStateJavaString => javaState.set(java)
    case expression: ProgrammingStateBeExpression => snapState.set(expression.toSnapXml)

  private def receive(next: ProgrammingState): Unit = {
    viewRequest += 1
    val request = viewRequest
    if enabledTabs.isEmpty then {
      setViewAvailable(false)
      conversionError.set(Some("No programming languages are enabled. Your source is unchanged."))
      return
    }
    val matchingTab = EvaEditor.tabFor(next)
    val nextTab = if enabledTabs.contains(matchingTab) then matchingTab else activeTab.now()
    def current: Boolean = request == viewRequest && Try(state.now()).toOption.contains(next)
    def complete(result: Try[ProgrammingState]): Unit =
      if current then
        result.flatMap(value => Try(show(value))) match
          case Success(_) =>
            val reopen = !viewAvailable.now() || activeTab.now() != nextTab
            if current && activeTab.now() != nextTab then activeTab.set(nextTab)
            if current then setViewAvailable(true)
            if current then conversionError.set(None)
            if current && reopen && mounted && viewOpen then openActiveView()
          case Failure(_) if current =>
            setViewAvailable(false)
            if current && activeTab.now() != nextTab then activeTab.set(nextTab)
            if current then conversionError.set(Some(s"This program cannot be displayed as ${nextTab.label} yet. Your source is unchanged."))
          case _ => ()

    if nextTab == matchingTab && !next.isInstanceOf[ProgrammingStateBeExpression] then complete(Success(next))
    else {
      setViewAvailable(false)
      if current then conversionError.set(None)
      if current then conversions(next, nextTab).foreach(complete)
    }
  }

  private lazy val snapEditor = Option.when(enabledTabs.contains(Tab.Snap))(
    EvaProgrammingTab.prepared(config, SnapLanguage, snapState, next => publish(Tab.Snap, next), Some(appearance))
  )
  private lazy val pythonEditor = Option.when(enabledTabs.contains(Tab.Python))(
    EvaProgrammingTab.prepared(config, AppLanguage.Python, pythonState, next => publish(Tab.Python, next), Some(appearance))
  )
  private lazy val javaEditor = Option.when(enabledTabs.contains(Tab.Java))(
    EvaProgrammingTab.prepared(config, AppLanguage.Java, javaState, next => publish(Tab.Java, next), Some(appearance))
  )
  private lazy val snapElement = snapEditor.map(_.domElement)
  private lazy val pythonElement = pythonEditor.map(_.domElement)
  private lazy val javaElement = javaEditor.map(_.domElement)

  def currentState(): ProgrammingState = state.now()

  private def currentViewState(): ProgrammingState =
    if !mounted then state.now()
    else if !viewAvailable.now() then throw IllegalStateException("This draft cannot be run in the selected editor.")
    else activeTab.now() match
      case Tab.Snap => snapState.now()
      case Tab.Python => pythonState.now()
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
      snapEditor.getOrElse(throw IllegalStateException("Snap is not enabled.")).captureSource()
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

  private[code] def select(tab: Tab): Future[Unit] = {
    if !enabledTabs.contains(tab) then return Future.unit
    val previousTab = activeTab.now()
    if previousTab == tab then {
      viewRequest += 1
      if !viewAvailable.now() then Try(state.now()).foreach(receive)
      return Future.unit
    }
    val source = Try {
      if mounted && previousTab == Tab.Snap && viewAvailable.now() then snapEditor.foreach(_.captureSource())
      state.now()
    }
    viewRequest += 1
    val request = viewRequest
    def failed(): Unit =
      if request == viewRequest && activeTab.now() == previousTab &&
          source.toOption.forall(original => Try(state.now()).toOption.contains(original)) then
        conversionError.set(Some(s"This draft cannot be converted to ${tab.label} yet. Your source is unchanged."))
    source match {
      case Failure(_) => failed(); Future.unit
      case Success(original) => conversions(original, tab).map { result =>
        if request == viewRequest && activeTab.now() == previousTab then {
          val current = Try {
            if mounted && previousTab == Tab.Snap && viewAvailable.now() then snapEditor.foreach(_.captureSource())
            state.now()
          }
          if request == viewRequest && current.toOption.contains(original) then
            result.flatMap(next => Try {
              def ifCurrent(expectedTab: Tab)(work: => Unit): Unit =
                if request == viewRequest && activeTab.now() == expectedTab && Try(state.now()).toOption.contains(original) then work
              cancelExecution()
              ifCurrent(previousTab) { if mounted then closeActiveView() }
              ifCurrent(previousTab) { show(next) }
              ifCurrent(previousTab) { conversionError.set(None) }
              ifCurrent(previousTab) { activeTab.set(tab) }
              ifCurrent(tab) { setViewAvailable(true) }
              ifCurrent(tab) { if mounted && viewOpen then openActiveView() }
            }) match {
              case Failure(_) => failed()
              case _ => ()
            }
        }
      }
    }
  }

  private def createTabButton(tab: Tab): Element =
    button(
      typ := "button",
      aria.pressed <-- activeTab.signal.map(active => (active == tab).toString),
      aria.selected <-- activeTab.signal.map(_ == tab),
      aria.label := tab.label,
      title := tab.label,
      if tab == Tab.Java then svg.svg(svg.cls := "eva-editor__language-icon", svg.viewBox := "0 0 24 24",
        svg.fill := "none", svg.stroke := "currentColor", svg.strokeWidth := "1.6",
        svg.strokeLineCap := "round", svg.strokeLineJoin := "round",
        svg.path(svg.d := "M5 11h11v5a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4Zm11 1h2a3 3 0 0 1 0 6h-3M4 23h14M8 8c-3-3 3-3 0-6M12 8c-3-3 3-3 0-6"))
      else span(tab.label),
      onClick --> (_ => select(tab)),
      disabled <-- state.signal.distinct.flatMapSwitch { source =>
        if EvaEditor.tabFor(source) == tab then Val(false)
        else Signal.fromFuture(conversions(source, tab)).map(_.forall(_.isFailure))
      }
    )

  private lazy val domElement: Element =
    div(
      cls <-- activeTab.signal.combineWith(conversionError.signal).map { (tab, error) =>
        "eva-editor editor-appearance" + (if tab == Tab.Java then " eva-editor--java" else "") +
          (if error.nonEmpty then " eva-editor--error" else "")
      },
      dataAttr("editor-theme") <-- appearance.theme,
      state.signal.distinct --> receive,
      onMountCallback { _ => mounted = true; viewOpen = true },
      onUnmountCallback { _ => mounted = false; viewOpen = false; viewRequest += 1; conversions.clear(); cancelExecution() },
      div(
        cls := "eva-editor__toolbar",
        div(cls := "eva-editor__tabs", aria.label := "Programming language", enabledTabs.map(createTabButton)),
        appearance.toggleButton()
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

  private def openActiveView(): Unit =
    if viewAvailable.now() then activeTab.now() match
      case Tab.Snap => snapEditor.foreach(_.onFullscreenOpen())
      case Tab.Java => javaEditor.foreach(_.onFullscreenOpen())
      case Tab.Python => ()

  override def onFullscreenOpen(): Unit = {
    viewOpen = true
    if viewAvailable.now() then openActiveView() else Try(state.now()).foreach(receive)
  }

  override def onFullscreenClose(): Unit = {
    viewOpen = false
    viewRequest += 1
    conversions.clear()
    cancelExecution()
    closeActiveView()
  }

  override def dismissOnOutsideClick: Boolean = false
}
