package it.evadid.homepage.webElements.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import org.scalajs.dom.MouseEvent

/**
 * Generic floating menu surface for dropdown content.
 *
 * To make the overlay appear above neighbouring components, render this element only while a trigger-owned
 * open-state is true, place it inside a parent container that has `position: relative`, and let the menu keep
 * its absolute positioning / z-index classes:
 *
 * {{{
 * val isOpen = Var(false)
 * div(
 *   cls := "dropdown-anchor",
 *   button(onClick --> (_ => isOpen.update(! _)), "Open menu"),
 *   child.maybe <-- isOpen.signal.map(open => Option.when(open)(HtmlDropdownMenu(items).getDomElement()))
 * )
 * }}}
 */
case class HtmlDropdownMenu(isOpen: Var[Boolean], content: Seq[HtmlAppElement]) extends HtmlAppElement {

  private val domElement: Element = div(
    cls := "dropdown-menu",
    role := "menu",
    children <-- Var(content.map(_.getDomElement())).signal
  )

  override def getDomElement(): Element = domElement
}

object HtmlDropdownMenu {

  private def strSignal(fullInfo: FullInfo, content: LanguageMapContentId): Signal[String] =
    fullInfo.signals.stringFromLanguageMapId(content)

  def menuLabel(labelString: Signal[String]): HtmlAppElement = new HtmlAppElement {
    private val domElement: Element = div(
      cls := "dropdown-menu-element dropdown-menu-label",
      role := "presentation",
      child.text <-- labelString
    )

    override def getDomElement(): Element = domElement
  }

  def menuLabel(fullInfo: FullInfo, id: String): HtmlAppElement = menuLabel(strSignal(fullInfo, LanguageMapContentId(id)))

  def menuItem(fullInfo: FullInfo, id: String, onAction: MouseEvent => Any): HtmlAppElement = menuItem(strSignal(fullInfo, LanguageMapContentId(id)), onAction)

  def menuItem(label: Signal[String], onAction: MouseEvent => Any = _ => ()): HtmlAppElement = new HtmlAppElement {
    private val domElement: Element = div(
      cls := "dropdown-menu-element dropdown-menu-item",
      role := "menuitem",
      typ := "button",
      onClick --> { event => onAction(event) },
      child.text <-- label
    )

    override def getDomElement(): Element = domElement
  }

  def menuItem(fullInfo: FullInfo, labelId: LanguageMapContentId, onAction: MouseEvent => Any): HtmlAppElement = {
    menuItem(strSignal(fullInfo, labelId), onAction)
  }


}
