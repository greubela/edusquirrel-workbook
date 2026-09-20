package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.file.{CopyrightInfo, LoadedFile}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.user.AllUserInfo
import it.evadid.homepage.control.info.WorkbookUserDataAnalyzer
import it.evadid.homepage.control.model.AllWorkbookInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlDropdownMenu
import it.evadid.homepage.workbook.syncDestination.LocalStorageSync
import org.scalajs.dom.{File, HTMLInputElement}

import scala.concurrent.ExecutionContext

case class DropdownMenuControl(curWorkbook: Option[AllWorkbookInfo], curUser: Option[AllUserInfo]) extends HtmlAppElement {

  private val isOpen: Var[Boolean] = Var(false)

  private val currentUserInitials: Signal[String] = fullInfo.signals.user.map(_.map(_.user.initials).getOrElse("[?]"))

  private val logger = fullInfo.loggerSystemInfo.uiAndDomLogger


  private def closeMenu(): Unit = isOpen.set(false)

  private def downloadAll(): Unit = {
    fullInfo.current.workbookUserData.foreach(_.downloadAllData())
  }



  private def switchUser(user: Option[AllUserInfo]): Unit = {
    fullInfo.usageControl.changeUser(user)
    closeMenu()
  }

  private def logout(): Unit = {
    switchUser(None)
  }


  private val userNameOrNobodySignal: Signal[String] = fullInfo.signals.stringFromMapWithFallback(
    fullInfo.signals.user.map(_.map(_.user.name).map(LanguageMap.universalMap)),
    fullInfo.signals.ensuredLanguageMapSignal(LanguageMapContentId("basic/noUserLoggedIn")),
  )

  private val menu: HtmlDropdownMenu = HtmlDropdownMenu(isOpen,
    createDefaultMenu() ++ createSessionMenu() //++ createDemoMenu()
  )

  private def createDefaultMenu(): List[HtmlAppElement] = List()


  private val uploadInput: ReactiveHtmlElement[HTMLInputElement] = laminarHelper.sessionFileUploadInput(logger, loadedFile => fullInfo.usageControl.tryContinueWithSessionFile(loadedFile))

  private def createSessionMenu(): List[HtmlAppElement] = {
    List(
      HtmlDropdownMenu.menuLabel(userNameOrNobodySignal),
      HtmlDropdownMenu.menuItem("basic/downloadEverything", _ => downloadAll()),
      HtmlDropdownMenu.menuItem("basic/buttonLocalUpload", _ => uploadInput.ref.click()),
      HtmlDropdownMenu.menuItem("basic/logout", _ => logout())
    )
  }


  /*private def createDemoMenu(): List[HtmlAppElement] = {
    List(
      HtmlDropdownMenu.menuLabel(laminarHelper.plaintextStringSignal("basic/switchUser"))
    ) ++
      HomepageDefaults.selectableUsers.map(user => HtmlDropdownMenu.menuItem(Var(user.user.name).signal, _ => switchUser(Some(user))))
  }*/

  private val domElement: Element = div(
    cls := "workbook-user-menu",
    uploadInput,
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
