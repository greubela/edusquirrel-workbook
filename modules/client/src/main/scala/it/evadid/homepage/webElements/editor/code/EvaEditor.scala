package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorProgrammingTab
import it.evadid.homepage.webElements.editor.code.EvaEditorConfig
import it.evadid.homepage.webElements.editor.code.SnapEditor.{SnapCodeEditor, SnapCodeEditorConfig}
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.workbook.elements.interactionElements.programming.*

import scala.concurrent.{ExecutionContext, Future}

/** Full-screen programming editor that owns one polymorphic state and presents
 * a suitable editor for every supported representation.
 */

object EvaEditor {

  def tabFor(evaConfig: EvaEditorConfig, programmingLanguage: ProgrammingLanguage, centralState: Var[ProgrammingState], handleOnStateChanged: ProgrammingState => Unit): EvaEditorProgrammingTab[? <: ProgrammingState] =
    programmingLanguage.match {
      case Java => {
        EvaEditorProgrammingTab[ProgrammingStateJavaString](
          centralState.now(),
          programmingLanguage,
          curVar => {
            val bound: Var[String] = curVar.bimap[String](_.code)(ProgrammingStateJavaString(_))
            CodeMirrorEditor(bound, code => handleOnStateChanged(ProgrammingStateJavaString(code)), language = AppLanguage.Java)
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

  private given ExecutionContext = ExecutionContext.global

  private val (snapEditor, availableTabs): (SnapCodeEditor, Map[ProgrammingLanguage, EvaEditorProgrammingTab[? <: ProgrammingState]]) = {
    val res2: Map[ProgrammingLanguage, EvaEditorProgrammingTab[? <: ProgrammingState]] = evaConfig.enabledLanguages.map(curLang => curLang -> EvaEditor.tabFor(evaConfig, curLang, state, handleOnStateUpdate)).toMap
    val res1: HtmlAppElement = res2(SnapLanguage).editorElement
    (res1.asInstanceOf[SnapCodeEditor], res2)
  }
  private val activeTab: Var[EvaEditorProgrammingTab[? <: ProgrammingState]] = Var(availableTabs(SnapLanguage))


  private def handleOnStateUpdate(next: ProgrammingState): Unit = {
    if (state.now() != next) {
      state.set(next)
    //  println(s"cur state: ${next}")
    }
    availableTabs.values.foreach(curTab => {
      if (curTab.associatedVar.now() != next)
        curTab.canSetStateTo(next).foreach(curTab.setStateTo)
    })
  }

  /** Small preview retained by the workbook card. */
  val previewCanvas: Element = snapEditor.previewCanvas

  /** The representation currently owned by the editor. All derived behavior starts here. */
  def currentState(): ProgrammingState = state.now()

  def getCurrentTurtleCommands(): Future[List[TurtleCommand[Double]]] = Future {
    currentState().toBeExpressionState.deriveTurtleCommands
  }

  private def select(programmingLanguage: ProgrammingLanguage): Unit =
    if (!(activeTab.now().associatedLanguage == programmingLanguage)) {
      val tabToSelect = availableTabs.get(programmingLanguage)
      if (tabToSelect.nonEmpty && tabToSelect.get.canSetStateTo(currentState()).nonEmpty) {
        tabToSelect.get.setStateTo(state.now())
        activeTab.set(tabToSelect.get)
      }
    }

  private def createTabButton(programmingLanguage: ProgrammingLanguage, tab: EvaEditorProgrammingTab[? <: ProgrammingState]): Element = {
    button(
      typ := "button",
      cls <-- activeTab.signal.map(active =>
        if active == tab then "eva-editor__tab eva-editor__tab--active" else "eva-editor__tab"
      ),
      aria.selected <-- activeTab.signal.map(_ == tab),
      tab.associatedLanguage.name,
      onClick --> (_ => select(programmingLanguage)),
      disabled <-- state.signal.map(curState => tab.canSetStateTo(curState).isEmpty)
    )
  }

  override def getDomElement(): Element =
    div(
      cls := "eva-editor",
      onMountCallback { ctx =>
        state.signal.changes.foreach(handleOnStateUpdate)(using ctx.owner)
      },
      div(
        cls := "eva-editor__tabs",
        evaConfig.enabledLanguages.map { curLang =>
          createTabButton(curLang, availableTabs(curLang))
        }
      ),
      div(
        cls := "eva-editor__content",
        child <-- activeTab.signal.map(_.editorElement.getDomElement())
      )
    )

  override def onFullscreenOpen(): Unit = {
    if activeTab.now().associatedLanguage == SnapLanguage then snapEditor.onFullscreenOpen()
  }

  override def onFullscreenClose(): Unit = {
    snapEditor.onFullscreenClose()
  }

  override def dismissOnOutsideClick: Boolean = false
}

