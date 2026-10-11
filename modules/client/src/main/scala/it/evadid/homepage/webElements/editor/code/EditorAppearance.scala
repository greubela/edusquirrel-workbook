package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.util.Try

final class EditorAppearance(initiallyDark: Boolean = EditorAppearance.prefersDark) {
  private val dark = Var(initiallyDark)
  val theme: Signal[String] = dark.signal.map(if _ then "dark" else "light")

  def toggleButton(): Element = button(
    typ := "button",
    cls := "editor-appearance__toggle",
    aria.label <-- dark.signal.map(if _ then "Use light appearance" else "Use dark appearance"),
    title <-- dark.signal.map(if _ then "Use light appearance" else "Use dark appearance"),
    span(cls := "editor-appearance__icon", aria.hidden := true,
      child.text <-- dark.signal.map(if _ then "☾" else "☀")),
    span(child.text <-- dark.signal.map(if _ then "Dark" else "Light")),
    onClick --> (_ => dark.update(!_))
  )
}

object EditorAppearance {
  private def prefersDark: Boolean =
    Try(dom.window.matchMedia("(prefers-color-scheme: dark)").matches).getOrElse(false)
}
