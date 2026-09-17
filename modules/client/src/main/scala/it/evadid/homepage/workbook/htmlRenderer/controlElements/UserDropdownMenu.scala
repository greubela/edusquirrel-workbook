package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.file.{CopyrightInfo, LoadedFile}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.homepage.control.info.WorkbookUserDataAnalyzer
import it.evadid.homepage.control.model.{AllUserInfo, FullInfo}
import it.evadid.homepage.control.singletons.{HomepageDefaults, HtmlFullWorkbookApp}
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlDropdownMenu
import it.evadid.homepage.workbook.syncDestination.LocalStorageSync
import org.scalajs.dom.{File, HTMLInputElement}

import javax.naming.ldap.ControlFactory
import scala.concurrent.ExecutionContext

case class UserDropdownMenu() extends HtmlAppElement {

  private val isOpen: Var[Boolean] = Var(false)

  private val currentUserInitials: Signal[String] = fullInfo.signals.currentUserInfo.map(_.map(_.user.initials).getOrElse("[?]"))

  private lazy val uploadInput: ReactiveHtmlElement[HTMLInputElement] = input(
    styleAttr := "display:none;",
    typ := "file",
    accept := "json",
    onChange --> { event =>
      println("event: " + event)
      val inputElement = event.target.asInstanceOf[org.scalajs.dom.html.Input]
      println("element: " + inputElement)
      if (inputElement.files.length > 0) {
        val file: File = inputElement.files.item(0)
        val fd = fullInfo.contentControl.fileFactory.fromFile(file, CopyrightInfo.unknownCopyrightInfo)
        println("[UGLY USERDROPDOWNMENU] fd: " + fd)
        fd.loadData().foreach(onNewUploadFileSelected)(using ExecutionContext.global)
      } else {
        print("[UGLY USERDROPDOWNMENU] no file selected :(")
      }
    }
  )

  private def closeMenu(): Unit = isOpen.set(false)

  private def downloadAll(): Unit = {
    fullInfo.current.workbookUserData.foreach(_.downloadAllData())
  }

  private def onNewUploadFileSelected(file: LoadedFile): Unit = {
    println("#######################")
    val logger = fullInfo.loggerSystemInfo.uiAndDomLogger
    logger.logWarn("WorkbookUserDataAnalyzer: now trying to load prio session data!")

    val sessionData = WorkbookUserDataAnalyzer.serializerSessionData.deserialize(file.fileDataAsUtf8String)
    fullInfo.usageControl.changeUser(Some(sessionData.currentUserInfo))

    /* if (sessionData.currentUserInfo.user.mail == userInfo.user.personId) {
       workbookInfo.loadedWorkbook.allContainedInteractions.foreach(curInteraction => {
         sessionData.interactionHistory.foreach(historyTup => if (historyTup._1 == curInteraction.interactionVariable.keyForSerialization) {
           curInteraction.interactionVariable.updateHistory(_.withAddedEvents(historyTup._2, curInteraction.serializer))
         })
       })
     }*/
    logger.logWarn("overwriting current user config with new user config!")


    sessionData.interactionHistory.foreachEntry((varId, serHist) => {
      fullInfo.homepageInfoNow().workbookInfo.foreach(curInfo => {
        curInfo.loadedWorkbook.allContainedInteractions.map(_.interactionVariable).foreach(curInteraction => {
          if (curInteraction.keyForSerialization == varId) {
            curInteraction.updateHistory(_.withAddedEvents(serHist, curInteraction.underlyingInteraction.serializer))
          }
        })
      })
    })
  }

  private def switchUser(user: Option[AllUserInfo]): Unit = {
    fullInfo.usageControl.changeUser(user)
    closeMenu()
  }

  private def logout(): Unit = {
    switchUser(None)
    LocalStorageSync.resetCompleteStorage()
  }


  private val userNameOrNobodySignal: Signal[String] = fullInfo.signals.stringFromMapWithFallback(
    fullInfo.signals.currentUserInfo.map(_.map(_.user.name).map(LanguageMap.universalMap)),
    fullInfo.signals.ensuredLanguageMapSignal(LanguageMapContentId("basic/noUserLoggedIn")),
  )

  private val menu: HtmlDropdownMenu = HtmlDropdownMenu(isOpen,
    createDefaultMenu() ++ createSessionMenu() //++ createDemoMenu()
  )

  private def createDefaultMenu(): List[HtmlAppElement] = List()

  private def createSessionMenu(): List[HtmlAppElement] = List(
    HtmlDropdownMenu.menuLabel(userNameOrNobodySignal),
    HtmlDropdownMenu.menuItem("basic/downloadEverything", _ => downloadAll()),
    HtmlDropdownMenu.menuItem("basic/uploadData", _ => uploadInput.ref.click()),
    HtmlDropdownMenu.menuItem("basic/logout", _ => logout())
  )


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
