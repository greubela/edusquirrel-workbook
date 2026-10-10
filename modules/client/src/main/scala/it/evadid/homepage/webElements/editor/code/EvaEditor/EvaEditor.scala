package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

import scala.concurrent.ExecutionContext

abstract class EvaEditor() extends HtmlAppElement with FullscreenLifecycle {

  def furtherDomElements(): List[Element]

  val state: Var[ProgrammingState]
  val config: EvaEditorConfig

  private given ExecutionContext = ExecutionContext.global

  private lazy val (snapEditor, availableTabs): (SnapCodeEditor, Map[ProgrammingLanguage, EvaEditorProgrammingTab[? <: ProgrammingState]]) = {
    val res2: Map[ProgrammingLanguage, EvaEditorProgrammingTab[? <: ProgrammingState]] =
      config.enabledLanguages.map(curLang => curLang -> EvaProgrammingTab.tabFor(config, curLang, state, handleOnStateUpdate)).toMap
    val res1: HtmlAppElement = res2(SnapLanguage).editorElement
    (res1.asInstanceOf[SnapCodeEditor], res2)
  }
  private lazy val activeTab: Var[EvaEditorProgrammingTab[? <: ProgrammingState]] = Var(availableTabs(SnapLanguage))

  private def handleOnStateUpdate(next: ProgrammingState): Unit = {
    if (state.now() != next) state.set(next)
    availableTabs.values.foreach(curTab => curTab.canSetStateTo(next).foreach(curTab.setStateTo))
  }


  private def select(programmingLanguage: ProgrammingLanguage): Unit =
    if (!(activeTab.now().associatedLanguage == programmingLanguage)) {
      val tabToSelect = availableTabs.get(programmingLanguage)
      if (tabToSelect.nonEmpty && tabToSelect.get.canSetStateTo(state.now()).nonEmpty) {
        tabToSelect.get.setStateTo(state.now())
        activeTab.set(tabToSelect.get)
      }
    }

  private def createTabButton(programmingLanguage: ProgrammingLanguage, tab: EvaEditorProgrammingTab[? <: ProgrammingState]): Element = {
    button(
      typ := "button",
      aria.pressed <-- activeTab.signal.map(active => (active == tab).toString),
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
        config.enabledLanguages.map { curLang =>
          createTabButton(curLang, availableTabs(curLang))
        }
      ),
      div(
        cls := "eva-editor__content",
        child <-- activeTab.signal.map(_.editorElement.getDomElement())
      ),
      furtherDomElements()
    )

  override def onFullscreenOpen(): Unit = {
    if activeTab.now().associatedLanguage == SnapLanguage then snapEditor.onFullscreenOpen()
  }

  override def onFullscreenClose(): Unit = {
    snapEditor.onFullscreenClose()
  }

  override def dismissOnOutsideClick: Boolean = false
}
