package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

/** The answer as of its last run. Drawings, comparisons and step replays follow this state instead of
  * every edit, so typing never starts the program. The stored answer itself stays the central Var.
  */
final class TurtleProgramRun(answer: Var[ProgrammingState]) {
  private val lastRun = Var(answer.now())

  /** The program to draw; starts with the answer present when the task is rendered. */
  val executed: StrictSignal[ProgrammingState] = lastRun.signal

  /** True while the answer contains edits that have not been run yet. */
  val outdated: Signal[Boolean] = answer.signal.combineWith(lastRun.signal).map(_ != _).distinct

  def run(): Unit = lastRun.set(answer.now())

  /** Run button with a hint shown while the drawing belongs to an older version of the program.
    * Unmounting runs the program once, so views outside the editor show the answer it was closed with.
    */
  def element(text: LanguageMapContentId => Signal[String]): Element =
    div(cls := "turtle-run",
      onUnmountCallback(_ => run()),
      button(typ := "button", cls := "turtle-run__button",
        child.text <-- text(TurtleProgramRun.runLabel),
        onClick --> (_ => run())),
      p(cls := "turtle-run__hint", role := "status", aria.live := "polite",
        cls("turtle-run__hint--hidden") <-- outdated.map(!_),
        child.text <-- text(TurtleProgramRun.outdatedText)))
}

object TurtleProgramRun {
  val runLabel: LanguageMapContentId = LanguageMapContentId("basic/turtleRun")
  val outdatedText: LanguageMapContentId = LanguageMapContentId("basic/turtleRunOutdated")
}
