package it.evadid.homepage.workbook.htmlRenderer.controlElements

import it.evadid.workbook.control.InteractionSignals.*

import it.evadid.homepage.control.model.FullInfo

import it.evadid.workbook.session.AllWorkbookInfo
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.basic.LabelSelection
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.file.{CopyrightInfo, LoadedFile}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.user.AllUserInfo
import it.evadid.homepage.control.info.WorkbookUserDataAnalyzer
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.{HtmlDropdownMenu, SessionCredentialsEditor}
import it.evadid.homepage.workbook.syncDestination.LocalStorageSyncHandler
import org.scalajs.dom.{File, HTMLInputElement}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success}
import org.scalajs.dom
import it.evadid.core.datastructures.state.StateHelper.RichObservableValue
import it.evadid.workbook.sync.SyncResetState

case class DropdownMenuControl(fullInfo: FullInfo, curWorkbook: Option[AllWorkbookInfo], curUser: Option[AllUserInfo]) extends HtmlAppElement {

  private val isOpen: Var[Boolean] = Var(false)
  private val credentialsOpen = Var(false)


  private val resetState = fullInfo.interactions.resetStateSignal
  private val resetLabel: Signal[String] = LabelSelection(resetState,
    Seq("basic/resetAllData", "basic/resetAllDataRunning", "basic/resetAllDataRetry"), laminarHelper(fullInfo).plaintextStringSignal) {
      case SyncResetState.Idle | SyncResetState.Completed => Some("basic/resetAllData")
      case _: SyncResetState.Running => Some("basic/resetAllDataRunning")
      case _: SyncResetState.Failed => Some("basic/resetAllDataRetry")
    }

  private def resetOwner = fullInfo.signals.user.now().map(_.user.personId)

  private def resetAllData(): Unit = {
    val message = fullInfo.homepageInfoNow().languageMapStore.languageMaps
      .get(LanguageMapContentId("basic/resetAllDataConfirm"))
      .flatMap(_.tryGetInLanguage(fullInfo.signals.currentLanguage.now()))
      .getOrElse("Clear all configured local answer stores and delete your saved answers and feedback across all workbooks? This cannot be undone.")
    if !resetState.now().isInstanceOf[SyncResetState.Running] && dom.window.confirm(message) then {
      val owner = resetOwner
      fullInfo.interactions.sync.resetAllData().onComplete {
        case Success(_) if resetOwner == owner => dom.window.location.reload()
        case Success(_) => ()
        case Failure(error) => logger.logExceptionWarn("Reset was not confirmed by every destination", error)
      }
    }
  }

  private val currentUserInitials: Signal[String] = fullInfo.signals.user.map(_.map(_.user.initials).getOrElse("[?]"))

  private val logger = fullInfo.loggerSystemInfo.uiAndDomLogger


  private def closeMenu(): Unit = isOpen.set(false)

  private def downloadAll(): Unit = {
    WorkbookUserDataAnalyzer().downloadAllData(fullInfo)
  }



  private def switchUser(user: Option[AllUserInfo]): Unit = {
    fullInfo.userSessionControl.changeUser(user)
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

  private def createDefaultMenu(): Seq[HtmlAppElement] = List(
    HtmlDropdownMenu.menuItem(fullInfo, "workbookSelection/backToCatalogue", _ => {
      fullInfo.workbookNavigationControl.changeWorkbook(None)
      closeMenu()
    })
  )


  private val uploadInput: ReactiveHtmlElement[HTMLInputElement] = laminarHelper(fullInfo).sessionFileUploadInput(logger, loadedFile => fullInfo.userSessionControl.tryContinueWithSessionFile(loadedFile))

  private def createSessionMenu(): Seq[HtmlAppElement] =
    List(
      HtmlDropdownMenu.menuLabel(userNameOrNobodySignal),
      HtmlDropdownMenu.menuItem(fullInfo, "basic/downloadEverything", _ => downloadAll())
    ) ++ Option.when(curUser.nonEmpty)(HtmlDropdownMenu.menuItem(resetLabel, _ => resetAllData())).toSeq ++ List(
      HtmlDropdownMenu.menuItem(fullInfo, "login/buttonLocalUpload", _ => uploadInput.ref.click()),
      HtmlDropdownMenu.menuItem(Val("Session connections"), _ => { closeMenu(); credentialsOpen.set(true) }),
      HtmlDropdownMenu.menuItem(fullInfo, "basic/logout", _ => logout())
    )



  /*private def createDemoMenu(): Seq[HtmlAppElement] = {
    List(
      HtmlDropdownMenu.menuLabel(laminarHelper(fullInfo).plaintextStringSignal("basic/switchUser"))
    ) ++
      HomepageDefaults.selectableUsers.map(user => HtmlDropdownMenu.menuItem(Var(user.user.name).signal, _ => switchUser(Some(user))))
  }*/

  private val domElement: Element = div(
    cls := "workbook-user-menu",
    uploadInput,
    child.maybe <-- resetState.map(state => Option.when(state.isInstanceOf[SyncResetState.Failed])(
      p(role := "alert", child.text <-- laminarHelper(fullInfo).plaintextStringSignal("basic/resetAllDataError")))),
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
    child.maybe <-- credentialsOpen.signal.map(open => Option.when(open)(SessionCredentialsEditor(fullInfo, () => credentialsOpen.set(false)).getDomElement())),
    child.maybe <-- isOpen.signal.map(open => Option.when(open)(menu.getDomElement()))
  )

  override def getDomElement(): Element = domElement
}
