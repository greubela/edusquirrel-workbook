package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import org.scalajs.dom

/** Eva's language tab list (WAI-ARIA tabs with a roving tab stop). Hovering or focusing a tab prepares its
  * conversion; a tab whose conversion failed is marked disabled and explains why.
  */
final class EvaLanguageTabs(
    tabs: Seq[EvaEditorTab],
    selected: Signal[Option[EvaEditorTab]],
    conversion: EvaConversionController,
    labels: EvaEditorLabels
) {
  private val focused = Var(Option.empty[ProgrammingLanguage])
  private val listId = s"eva-tabs-${java.util.UUID.randomUUID()}"

  private def domId(language: ProgrammingLanguage): String = s"$listId-${language.name.replace(' ', '-')}"

  private def focus(language: ProgrammingLanguage): Unit = {
    focused.set(Some(language))
    Option(dom.document.getElementById(domId(language))).foreach(_.asInstanceOf[dom.html.Element].focus())
  }

  private def move(current: ProgrammingLanguage, direction: Int): Unit = if tabs.nonEmpty then {
    val index = tabs.indexWhere(_.language == current).max(0)
    focus(tabs(Math.floorMod(index + direction, tabs.length)).language)
  }

  private def tabButton(tab: EvaEditorTab, active: Signal[Option[ProgrammingLanguage]]): Element = {
    val reason = conversion.blockedBy(tab).combineWith(labels.conversion).map((failure, texts) => failure.map(texts.describe))
    val reasonId = s"${domId(tab.language)}-reason"
    val caption = tab.label(labels)
    button(
      typ := "button", role := "tab", idAttr := domId(tab.language),
      aria.label <-- caption,
      title <-- reason.combineWith(caption).map((blocked, text) => blocked.getOrElse(text)),
      aria.selected <-- selected.map(_.contains(tab)),
      aria.disabled <-- reason.map(_.nonEmpty),
      aria.describedBy := reasonId,
      tabIndex <-- active.map(current => if current.contains(tab.language) then 0 else -1),
      tab.icon, Option.when(tab.showLabel)(span(child.text <-- caption)),
      span(idAttr := reasonId, cls := "eva-editor__sr-only", child.text <-- reason.map(_.getOrElse(""))),
      onKeyDown.compose(_.withCurrentValueOf(active)) --> { (event, current) =>
        val from = current.getOrElse(tab.language)
        event.key match {
          case "ArrowRight" | "ArrowDown" => event.preventDefault(); move(from, 1)
          case "ArrowLeft" | "ArrowUp" => event.preventDefault(); move(from, -1)
          case "Home" => event.preventDefault(); tabs.headOption.foreach(first => focus(first.language))
          case "End" => event.preventDefault(); tabs.lastOption.foreach(last => focus(last.language))
          case _ => ()
        }
      },
      onFocus --> (_ => conversion.check(tab)),
      onPointerEnter --> (_ => conversion.check(tab)),
      onClick --> (_ => conversion.switchTo(tab)))
  }

  def element: Element = {
    // The focused tab holds the tab stop; otherwise the selected one, otherwise the first.
    val active = focused.signal.combineWith(selected).map((focus, current) =>
      focus.orElse(current.map(_.language)).orElse(tabs.headOption.map(_.language)))
    div(cls := "eva-editor__tabs eva-editor__segments", role := "tablist", aria.label <-- labels.languages,
      selected.changes --> (_ => focused.set(None)),
      tabs.map(tabButton(_, active)))
  }
}
