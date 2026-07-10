package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.homepage.control.model.{AllUserInfo, FullInfo}
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlDropdownMenu

case class UserDropdownMenu() extends HtmlAppElement with ControlFactory{

  private val isOpen: Var[Boolean] = Var(false)

  private val currentUserInitials: Signal[String] = fullInfo.signals.currentUserInfo.map(_.map(_.user.initials).getOrElse("[?]"))

  private def closeMenu(): Unit = isOpen.set(false)

  private def switchUser(user: Option[AllUserInfo]): Unit = {
    fullInfo.control.changeUser(user)
    closeMenu()
  }

  private val userNameOrNobodySignal: Signal[String] = fullInfo.signals.stringFromMapWithFallback(
    fullInfo.signals.currentUserInfo.map(_.map(_.user.name).map(LanguageMap.universalMap)),
    fullInfo.signals.ensuredLanguageMapSignal(LanguageMapContentId("basic/noUserLoggedIn")),
  )

  private val menu: HtmlDropdownMenu = HtmlDropdownMenu(isOpen,
    createDefaultMenu() ++ createSessionMenu() ++ createDemoMenu()
  )

  private def createDefaultMenu(): List[HtmlAppElement] = List()

  private def createSessionMenu(): List[HtmlAppElement] = List(
    HtmlDropdownMenu.menuLabel(userNameOrNobodySignal),
    HtmlDropdownMenu.menuItem("basic/downloadEverything", _ => fullInfo.cacheControl.downloadAllAvailableData()),
    HtmlDropdownMenu.menuItem("basic/logout", _ => switchUser(None))
  )


  private def createDemoMenu(): List[HtmlAppElement] = {
    List(
      HtmlDropdownMenu.menuLabel(labelString(LanguageMapContentId("basic/switchUser")))
    ) ++
      fullInfo.defaults.selectableUsers.map(user => HtmlDropdownMenu.menuItem(Var(user.user.name).signal, _ => switchUser(Some(user))))

  }

  private val domElement: Element = div(
    cls := "workbook-user-menu",
    div(
      cls := "workbook-user-menu-button",
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
