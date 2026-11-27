package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*

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

  /** Show/hide pair for a class diagram whose visibility is `open`. */
  def diagramToggle(open: Var[Boolean], label: Signal[String], show: Signal[String], hide: Signal[String], controls: String): Element =
    segments(label, "group", Seq(true -> show, false -> hide).map { (visible, caption) =>
      button(typ := "button", aria.controls := controls, aria.pressed <-- open.signal.map(open => (open == visible).toString),
        child.text <-- caption, onClick --> (_ => open.set(visible)))
    })
}
