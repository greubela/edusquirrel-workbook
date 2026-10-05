package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.homepage.webElements.editor.code.SnapEditor.{SnapCodeEditor, SnapCodeEditorConfig}
import it.evadid.workbook.elements.interactionElements.programming.*

import scala.concurrent.Future

/** Full-screen programming editor that owns one polymorphic state and presents
  * a suitable editor for every supported representation.
  */
final class EvaEditor(
    val state: Var[ProgrammingState],
    snapConfig: SnapCodeEditorConfig,
    onStateEdited: ProgrammingState => Unit = _ => ()
) extends HtmlAppElement with FullscreenLifecycle {

  private val activeTab = Var(EvaEditor.tabFor(state.now()))
  private val snapState = Var(state.now().toSnapXml)
  private val pythonState = Var(state.now().toPython.code)
  private val javaState = Var(state.now().toJava.code)

  private def publish(next: ProgrammingState): Unit =
    state.set(next)
    onStateEdited(next)

  private def receive(next: ProgrammingState): Unit =
    val nextTab = EvaEditor.tabFor(next)
    // Re-emitting the selected tab replaces Laminar's child node. Avoid doing
    // that for edits in the current CodeMirror instance, or it loses focus.
    if activeTab.now() != nextTab then activeTab.set(nextTab)
    nextTab match
      case EvaEditor.Tab.Snap => snapState.set(next.toSnapXml)
      case EvaEditor.Tab.Python => pythonState.set(next.toPython.code)
      case EvaEditor.Tab.Java => javaState.set(next.toJava.code)

  private val snapEditor = SnapCodeEditor(
    snapState,
    snapConfig,
    onStateEdited = next => publish(next)
  )
  private val pythonEditor = CodeMirrorEditor(
    pythonState,
    code => publish(ProgrammingStatePythonString(code)),
    language = AppLanguage.Python
  )
  private val javaEditor = CodeMirrorEditor(
    javaState,
    code => publish(ProgrammingStateJavaString(code)),
    language = AppLanguage.Java
  )

  /** Small preview retained by the workbook card. */
  val previewCanvas: Element = snapEditor.previewCanvas

  /** The representation currently owned by the editor. All derived behavior starts here. */
  def currentState(): ProgrammingState = state.now()

  def getCurrentTurtleCommands(): Future[List[TurtleCommand[Double]]] =
    snapEditor.getCurrentTurtleCommands()

  private def select(tab: EvaEditor.Tab): Unit =
    val current = state.now()
    tab match
      case EvaEditor.Tab.Snap => snapState.set(current.toSnapXml)
      case EvaEditor.Tab.Python => pythonState.set(current.toPython.code)
      case EvaEditor.Tab.Java => javaState.set(current.toJava.code)
    activeTab.set(tab)

  override def getDomElement(): Element =
    div(
      cls := "eva-editor",
      onMountCallback { ctx =>
        state.signal.changes.foreach(receive)(using ctx.owner)
      },
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
      div(
        cls := "eva-editor__content",
        child <-- activeTab.signal.map {
          case EvaEditor.Tab.Snap => snapEditor.getDomElement()
          case EvaEditor.Tab.Python => pythonEditor.getDomElement()
          case EvaEditor.Tab.Java => javaEditor.getDomElement()
        }
      )
    )

  override def onFullscreenOpen(): Unit =
    if activeTab.now() == EvaEditor.Tab.Snap then snapEditor.onFullscreenOpen()

  override def onFullscreenClose(): Unit = snapEditor.onFullscreenClose()
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
