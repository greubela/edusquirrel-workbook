package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.code.JavaFunctionBasedEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.PyodideTurtleCommandRunner
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.workbook.elements.interactionElements.programming.*

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/** Full-screen programming editor that owns one polymorphic state and presents
 * a suitable editor for every supported representation.
 */

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

  def tabFor(evaConfig: EvaEditorConfig, programmingLanguage: ProgrammingLanguage, centralState: Var[ProgrammingState], handleOnStateChanged: ProgrammingState => Unit): EvaEditorProgrammingTab[? <: ProgrammingState] =
    programmingLanguage.match {
      case Java => {
        EvaEditorProgrammingTab[ProgrammingStateJavaString](
          centralState.now(),
          programmingLanguage,
          curVar => {
            val bound: Var[ProgrammingState] = curVar.bimap[ProgrammingState](identity)(_.toJava)
            new JavaFunctionBasedEditor(bound, onStateEdited = handleOnStateChanged)
          },
          _.toJava
        )
      }
      case Python => {        
        EvaEditorProgrammingTab[ProgrammingStatePythonString](
          centralState.now(),
          programmingLanguage,
          curVar => {
            val bound: Var[String] = curVar.bimap[String](_.code)(ProgrammingStatePythonString(_))
            CodeMirrorEditor(bound, code => handleOnStateChanged(ProgrammingStatePythonString(code)), language = AppLanguage.Python)
          },
          _.toPython
        )
      }
      case SnapLanguage => {
        EvaEditorProgrammingTab[ProgrammingStateSnapXml](
          centralState.now(),
          programmingLanguage,
          curVar => SnapCodeEditor(curVar, evaConfig.snapConfig, handleOnStateChanged),
          _.toSnapXml
        )
      }

      case _ => throw UnsupportedOperationException(s"ProgrammingLanguage '${programmingLanguage}' not supported yet in EvaEditor!'")

    }

  case class EvaEditorProgrammingTab[T <: ProgrammingState](
                                                             private val initState: ProgrammingState,
                                                             associatedLanguage: ProgrammingLanguage,
                                                             createEditor: Var[T] => HtmlAppElement,
                                                             convertFrom: ProgrammingState => T
                                                           ) {

    val associatedVar: Var[T] = Var(convertFrom(initState))

    val editorElement: HtmlAppElement = createEditor(associatedVar)

    lazy val domElement: Element = editorElement.getDomElement()
    
    def canSetStateTo(state: ProgrammingState): Option[T] = try {
      Some(convertFrom(state))
    } catch case (err: Throwable) => {
      None
    }

    def setStateTo(state: ProgrammingState): Unit = canSetStateTo(state).foreach(associatedVar.set)

  }

}

final class EvaEditor(
                       val state: Var[ProgrammingState],
                       evaConfig: EvaEditorConfig,
                       onStateEdited: ProgrammingState => Unit = _ => ()
) extends HtmlAppElement with FullscreenLifecycle {

  import EvaEditor.Tab

  private val enabledTabs = evaConfig.enabledLanguages.distinct.map { language =>
    Tab.values.find(_.programmingLanguage == language).getOrElse(
      throw UnsupportedOperationException(s"ProgrammingLanguage '$language' not supported yet in EvaEditor!")
    )
  }
  private val initialTab = EvaEditor.tabFor(state.now())
  private[code] val activeTab = Var(
    if enabledTabs.contains(initialTab) then initialTab else enabledTabs.headOption.getOrElse(initialTab)
  )
  private[code] val conversionError = Var(Option.empty[String])
  private val viewAvailable = Var(
    enabledTabs.contains(initialTab) && !state.now().isInstanceOf[ProgrammingStateBeExpression]
  )
  private val snapState = Var(state.now() match
    case snap: ProgrammingStateSnapXml => snap
    case floating: ProgrammingStateSnapXMLWithAdditionalFloatingObjects => floating.toSnapXml
    case _ => ProgrammingExerciseState.empty
  )
  private val pythonState = Var(state.now() match
    case ProgrammingStatePythonString(code) => code
    case _ => ""
  )
  private val javaState = Var[ProgrammingState](state.now() match
    case java: ProgrammingStateJavaString => java
    case _ => ProgrammingStateJavaString("")
  )
  private lazy val pythonRunner = new PyodideTurtleCommandRunner()
  private var mounted = false

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
    case Tab.Python => state.toPython
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

  private val snapEditor = Option.when(enabledTabs.contains(Tab.Snap))(
    SnapCodeEditor(snapState, evaConfig.snapConfig, next => publish(Tab.Snap, next))
  )
  private val pythonEditor = Option.when(enabledTabs.contains(Tab.Python))(
    CodeMirrorEditor(pythonState, code => publish(Tab.Python, ProgrammingStatePythonString(code)), language = AppLanguage.Python)
  )
  private val javaEditor = Option.when(enabledTabs.contains(Tab.Java))(
    new JavaFunctionBasedEditor(javaState, onStateEdited = next => publish(Tab.Java, next))
  )
  private lazy val snapElement = snapEditor.map(_.getDomElement())
  private lazy val pythonElement = pythonEditor.map(_.getDomElement())
  private lazy val javaElement = javaEditor.map(_.getDomElement())

  /** Small preview retained by the workbook card. */
  lazy val previewCanvas: Element = snapEditor.fold[Element](div("Preview unavailable for this draft.")) { editor =>
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
        if ready then editor.previewCanvas
        else div("Preview unavailable for this draft.")
      }
    )
  }

  /** The representation currently owned by the editor. All derived behavior starts here. */
  def currentState(): ProgrammingState = state.now()

  private def deriveCommands(source: ProgrammingState): Future[List[TurtleCommand[Double]]] =
    Try(source.toBeExpressionState.deriveTurtleCommands).fold(Future.failed, Future.successful)

  def getCurrentTurtleCommands(): Future[List[TurtleCommand[Double]]] =
    if !mounted then state.now() match
      case snap: ProgrammingStateSnapXml => SnapCodeEditor.commandsFor(snap)
      case floating: ProgrammingStateSnapXMLWithAdditionalFloatingObjects => SnapCodeEditor.commandsFor(floating.toSnapXml)
      case ProgrammingStatePythonString(code) => pythonRunner.execute(code)
      case java: ProgrammingStateJavaString => deriveCommands(java)
      case expression: ProgrammingStateBeExpression => deriveCommands(expression)
    else if !viewAvailable.now() then Future.failed(IllegalStateException("This draft cannot be run in the selected editor."))
    else activeTab.now() match
      case Tab.Snap => snapEditor.fold(Future.failed[List[TurtleCommand[Double]]](IllegalStateException("Snap is not enabled.")))(_.getCurrentTurtleCommands())
      case Tab.Python => pythonRunner.execute(pythonState.now())
      case Tab.Java => deriveCommands(javaState.now())

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
      onUnmountCallback { _ => mounted = false },
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
      )
    )

  override def getDomElement(): Element = domElement

  override def onFullscreenOpen(): Unit =
    if viewAvailable.now() then activeTab.now() match
      case Tab.Snap => snapEditor.foreach(_.onFullscreenOpen())
      case Tab.Java => javaEditor.foreach(_.onFullscreenOpen())
      case Tab.Python => ()

  override def onFullscreenClose(): Unit = closeActiveView()

  override def dismissOnOutsideClick: Boolean = false
}
