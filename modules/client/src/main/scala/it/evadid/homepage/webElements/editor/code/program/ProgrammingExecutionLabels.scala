package it.evadid.homepage.webElements.editor.code.program

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId

/** UI strings of [[ProgrammingExecutionPanel]], resolved from the `blockeditor` language map. */
final case class ProgrammingExecutionLabels(
    console: Signal[String],
    run: Signal[String],
    stop: Signal[String],
    running: Signal[String],
    stopped: Signal[String],
    unavailable: Signal[String],
    download: Signal[String]
)

object ProgrammingExecutionLabels {
  def fromContent(text: LanguageMapContentId => Signal[String]): ProgrammingExecutionLabels = {
    def label(key: String) = text(LanguageMapContentId("blockeditor", key))
    ProgrammingExecutionLabels(label("console"), label("run"), label("stop"), label("executionRunning"),
      label("executionStopped"), label("executionUnavailable"), label("downloadProject"))
  }
}
