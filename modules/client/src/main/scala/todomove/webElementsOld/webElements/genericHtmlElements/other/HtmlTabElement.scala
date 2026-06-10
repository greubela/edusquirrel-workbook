package todomove.webElementsOld.webElements.genericHtmlElements.other

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement

case class HtmlTab(tabNr: Int, tabDiv: L.HtmlElement, tabLabel: String)

case class HtmlTabElement(tabs: List[HtmlTab], onTabSwitched: (HtmlTab, HtmlTab) => Any) extends HtmlAppElement {

  override def getDomElement(): L.Element = {
    require(tabs.nonEmpty, "HtmlTabElement requires at least one tab")

    val activeTabVar = Var(tabs.head)

    val headerRow = div(
      cls := "be-tab-element__labels",
      tabs.map(tab =>
        button(
          typ := "button",
          cls := "be-tab-element__label",
          cls("be-tab-element__label--active") <-- activeTabVar.signal.map(_.tabNr == tab.tabNr),
          tab.tabLabel,
          onClick --> { _ =>
            val current = activeTabVar.now()
            if (current.tabNr != tab.tabNr) {
              activeTabVar.set(tab)
              onTabSwitched(current, tab)
            }
          }
        )
      )
    )

    val contentPane = div(
      cls := "be-tab-element__content",
      child <-- activeTabVar.signal.map(_.tabDiv)
    )

    div(
      cls := "be-tab-element",
      headerRow,
      contentPane
    )
  }

}
