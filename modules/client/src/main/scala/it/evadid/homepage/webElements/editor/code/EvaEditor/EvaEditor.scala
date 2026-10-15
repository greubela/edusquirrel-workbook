package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.util.logging.Logger
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

import scala.concurrent.{ExecutionContext, Future}

case class EvaEditor(logger: Logger, state: Var[ProgrammingState], modules: Seq[EvaEditorModule], config: EvaEditorConfig) extends HtmlAppElement {

  private given ExecutionContext = ExecutionContext.global

  val appearance = EditorAppearance()
  private val internalState: Var[EvaEditorState] = state.bimap(curProg => EvaEditorState(curProg.associatedLanguage, curProg))(_.currentProgram)
  private val lastError: Var[Option[Throwable]] = Var(None)

  private val editorMap: Map[ProgrammingLanguage, EvaEditorTab[?]] = {
    config.enabledLanguages.map(curLanguage => {
      curLanguage -> EvaEditorConfig.defaultEditorFor(logger, curLanguage, this)
    }).toMap
  }

  def switchTabTo(programmingLanguage: ProgrammingLanguage): Unit = Future {
    val tab = editorMap(programmingLanguage)
    try {
      var newState = state.now().toLanguage(programmingLanguage)
      tab.setWith(newState)
      lastError.set(None)
    } catch case (err: Throwable) => {
      lastError.set(Some(err))
    }
  }

  private def createTabButton(programmingLanguage: ProgrammingLanguage, tab: EvaEditorTab[?]): Element =
    button(
      typ := "button",
      aria.pressed <-- state.signal.map(_.associatedLanguage == programmingLanguage).map(_.toString),
      aria.selected <-- state.signal.map(_.associatedLanguage == programmingLanguage),
      aria.label := tab.label,
      title := tab.label,
      tab.icon.getOrElse(div()),
      span(tab.label),
      onClick --> (_ => switchTabTo(programmingLanguage)),
    )

  private val activeTab: Signal[EvaEditorTab[?]] = state.signal.map(currentState => editorMap(currentState.associatedLanguage))

  private lazy val domElement: Element =
    div(
      cls <-- activeTab.combineWith(lastError.signal).map { (tab, error) =>
        "eva-editor editor-appearance" + tab.additionalCssForEvaEditor +
          (if error.nonEmpty then " eva-editor--error" else "")
      },
      dataAttr("editor-theme") <-- appearance.theme,
      state.signal.distinct --> { progState => editorMap(progState.associatedLanguage).setWith(progState) },
      activeTab.flatMapSwitch(_.underlyingSignal) --> { next => next.foreach(updateFromEditor => if (state.now() != updateFromEditor) state.set(updateFromEditor)) },
      div(
        cls := "eva-editor__toolbar",
        div(cls := "eva-editor__tabs", aria.label := "Programming language", editorMap.toList.map(createTabButton)),
        appearance.toggleButton()
      ),
      child.maybe <-- lastError.signal.map(_.map(err =>
        div(cls := "eva-editor__error", role := "alert", span(err.getMessage))
      )),
      div(
        cls := "eva-editor__content",
        child <-- activeTab.map(_.getDomElement())
      ),
      modules.map(_.getDomElement)
    )

  override def getDomElement(): Element = domElement

}
