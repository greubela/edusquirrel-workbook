package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

/** The answer as of its last run: drawings follow it instead of every edit, and `grade` grades it.
  * Unmounting runs once, so views outside the editor show the answer it was closed with.
  */
final class TurtleProgramRun(answer: Var[ProgrammingState], grade: () => Unit) {
  private val lastRun = Var(answer.now())

  val executed: StrictSignal[ProgrammingState] = lastRun.signal

  private val outdated: Signal[Boolean] = answer.signal.combineWith(lastRun.signal).map(_ != _).distinct

  def run(): Unit = {
    lastRun.set(answer.now())
    grade()
  }

  def element(text: LanguageMapContentId => Signal[String]): Element =
    div(cls := "turtle-run",
      onUnmountCallback(_ => run()),
      button(typ := "button", child.text <-- text(TurtleProgramRun.runLabel), onClick --> (_ => run())),
      p(cls := "turtle-run__hint", role := "status", aria.live := "polite", hidden <-- outdated.map(!_),
        child.text <-- text(TurtleProgramRun.outdatedText)))
}

object TurtleProgramRun {
  val runLabel: LanguageMapContentId = LanguageMapContentId("basic/turtleRun")
  val outdatedText: LanguageMapContentId = LanguageMapContentId("basic/turtleRunOutdated")
}
