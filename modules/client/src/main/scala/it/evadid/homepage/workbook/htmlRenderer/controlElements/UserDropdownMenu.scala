package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.AllUserInfo
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlDropdownMenu


case class UserDropdownMenu() extends HtmlAppElement {

  private val isOpen: Var[Boolean] = Var(false)

  private val currentUserInitials: Signal[String] = fullInfo.signals.currentUserInfo.map(_.map(_.user.initials).getOrElse("[?]"))

  def labelString(contentId: LanguageMapContentId): Signal[String] = fullInfo.signals.stringFromLanguageMapId(contentId)

  private def closeMenu(): Unit = isOpen.set(false)

  private def switchUser(user: AllUserInfo): Unit = {
    fullInfo.control.changeUser(Some(user))
    closeMenu()
  }

  private def label(contentId: LanguageMapContentId): HtmlAppElement = new HtmlAppElement {
    private val domElement: Element = div(
      cls := "html-dropdown-menu-label",
      role := "presentation",
      child.text <-- labelString(contentId)
    )

    override def getDomElement(): Element = domElement
  }

  private val menu: HtmlDropdownMenu = HtmlDropdownMenu(
    List(
      HtmlDropdownMenu.menuItem(labelString(LanguageMapContentId("basic/settings"))),
      HtmlDropdownMenu.menuItem(labelString(LanguageMapContentId("basic/downloadEverything"))),
      HtmlDropdownMenu.menuItem(labelString(LanguageMapContentId("basic/logout")), _ => {
        fullInfo.control.changeUser(None)
        closeMenu()
      }),
      label(LanguageMapContentId("basic/switchUser"))
    ) ++ fullInfo.defaults.selectableUsers.map(user =>
      HtmlDropdownMenu.menuItem(Var(user.user.name).signal, _ => switchUser(user))
    )
  )

  private val domElement: Element = div(
    cls := "workbook-user-menu-anchor dropdown-anchor",
    button(
      cls := "workbook-user-menu-trigger",
      typ := "button",
      aria.label := "Benutzermenü öffnen",
      title <-- currentUserInitials.map(name => s"Benutzermenü für $name öffnen"),
      onClick --> { event =>
        event.stopPropagation()
        isOpen.update(!_)
      },
      span(cls := "workbook-user-menu-icon", child.text <-- currentUserInitials),
      span(cls := "workbook-user-menu-caret", "▾")
    ),
    child.maybe <-- isOpen.signal.map(open => Option.when(open)(menu.getDomElement()))
  )

  override def getDomElement(): Element = domElement
}
