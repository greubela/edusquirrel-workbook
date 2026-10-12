package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.webElements.editor.code.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.{ProgrammingStateJavaString, ProgrammingStatePythonString}

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


  /** Publish the visible editor before another tab reads the shared program. */
  private def commitActiveEditor(): Unit =
    activeTab.now().editorElement match
      case snap: SnapCodeEditor => snap.flushPendingEdits()
      case code: CodeMirrorEditor => publishCodeMirror(code)
      case _ => ()

  private def publishCodeMirror(code: CodeMirrorEditor): Unit =
    code.currentDoc.foreach { text =>
      if !codeMatchesSharedState(code, text) then
        val next = code.language match
          case Java => ProgrammingStateJavaString(text)
          case _ => ProgrammingStatePythonString(text)
        handleOnStateUpdate(next)
    }

  /** True when the open text doc is already what the shared program renders as. */
  private def codeMatchesSharedState(code: CodeMirrorEditor, text: String): Boolean =
    try
      val rendered = code.language match
        case Java => state.now().toJava.code
        case _ => state.now().toPython.code
      rendered == text
    catch
      case _: Throwable => false

  private def select(programmingLanguage: ProgrammingLanguage): Unit =
    if activeTab.now().associatedLanguage != programmingLanguage then
      val _ = EvaEditorSync.showLanguage(
        commitLeaving = () => commitActiveEditor(),
        destinationAccepts = candidate => availableTabs.get(programmingLanguage).exists(_.canSetStateTo(candidate).nonEmpty),
        applyDestination = next => availableTabs.get(programmingLanguage).foreach(_.setStateTo(next)),
        revealDestination = () => availableTabs.get(programmingLanguage).foreach(activeTab.set),
        afterReveal = () => if programmingLanguage == SnapLanguage then snapEditor.onFullscreenOpen(),
        state = () => state.now()
      )

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

  override def onFullscreenClose(): Unit =
    if activeTab.now().associatedLanguage == SnapLanguage then snapEditor.onFullscreenClose()
    else
      commitActiveEditor()
      snapEditor.pauseWithoutPublishing()

  override def dismissOnOutsideClick: Boolean = false
}

/** Tab switches commit the editor being left before the destination reads shared state. */
private[code] object EvaEditorSync {
  def showLanguage(
      commitLeaving: () => Unit,
      destinationAccepts: ProgrammingState => Boolean,
      applyDestination: ProgrammingState => Unit,
      revealDestination: () => Unit,
      afterReveal: () => Unit,
      state: () => ProgrammingState
  ): Boolean =
    commitLeaving()
    val current = state()
    if !destinationAccepts(current) then false
    else
      applyDestination(current)
      revealDestination()
      afterReveal()
      true
}
