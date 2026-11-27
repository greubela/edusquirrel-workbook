package it.evadid.homepage.webElements.download

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaActionIcons
import it.evadid.homepage.webElements.editor.code.program.ProgrammingExecutionLabels
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

/** Downloads the committed program in the format of its current language ([[ProjectDownload]]); a failed export
  * is explained next to the button.
  */
object ProjectDownloadButton {
  def apply(source: Signal[ProgrammingState], labels: ProgrammingExecutionLabels, projectTitle: String = "program",
      saver: ProjectDownload.Saver = ProjectDownload.BrowserSaver): Element = {
    val failure = Var(Option.empty[String])
    span(cls := "eva-editor__download",
      source.changes --> (_ => failure.set(None)),
      button(typ := "button", cls := "programming-execution__download", title <-- labels.download,
        disabled <-- source.map(state => !ProjectDownload.supports(state.associatedLanguage)),
        EvaActionIcons.download, span(child.text <-- labels.download),
        onClick.compose(_.sample(source)) --> (state =>
          failure.set(ProjectDownload.download(state, state.associatedLanguage, projectTitle, saver = saver).left.toOption))),
      span(cls := "programming-execution__errors", role := "alert", hidden <-- failure.signal.map(_.isEmpty),
        child.text <-- failure.signal.map(_.getOrElse(""))))
  }
}
