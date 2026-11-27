package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.basic.MediaQuery

/** A language editor whose controls (editing modes, class diagram display) join Eva's language tabs in its single
  * toolbar line. Each control group is an `eva-editor__segments` group styled like the language tabs.
  */
trait EvaToolbarControls {
  /** Created once per editor; Eva shows it while the editor is selected. */
  def toolbarControls: Element
}

object EvaToolbarControls {
  /** A segmented group of `buttons` with an accessible name, styled like Eva's language tabs. */
  def segments(label: Signal[String], groupRole: String, buttons: Seq[Element]): Element =
    div(cls := "eva-editor__segments", role := groupRole, aria.label <-- label, buttons)

  /** Whether an editor's class diagram is shown. On a small screen (narrow, or low like a laptop) it starts hidden
    * and hides again after each entry chosen in it, so the source keeps the space; elsewhere it stays as set.
    */
  final class DiagramDisplay(compactScreen: Signal[Boolean] = MediaQuery.matches(DiagramDisplay.smallScreen)) {
    private val shown = Var(true)
    val open: Signal[Boolean] = shown.signal

    /** Hides the diagram on a small screen now and whenever `choices` emits there. */
    def binders(choices: EventStream[Any]): Seq[Binder[HtmlElement]] = Seq(
      compactScreen --> (compact => if compact then shown.set(false)),
      choices.withCurrentValueOf(compactScreen) --> ((_, compact) => if compact then shown.set(false)))

    /** The Show/Hide pair for the diagram element `controls`. */
    def toggle(label: Signal[String], show: Signal[String], hide: Signal[String], controls: String): Element =
      segments(label, "group", Seq(true -> show, false -> hide).map { (visible, caption) =>
        button(typ := "button", aria.controls := controls, aria.pressed <-- shown.signal.map(open => (open == visible).toString),
          child.text <-- caption, onClick --> (_ => shown.set(visible)))
      })
  }

  object DiagramDisplay {
    val smallScreen: String = "(max-width: 760px), (max-height: 720px)"
  }
}
