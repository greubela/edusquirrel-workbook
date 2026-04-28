package workbook.model.info

import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import org.scalajs.dom
import workbook.htmlElements.container.HtmlFullScreenContainerElement
import workbook.model.info.FullInfo.HomepageDefaults
import workbook.model.info.control.{HomepageCurrentInfo, HomepageDataControl, HomepageSignalInfo}
import workbook.model.interaction.sync.SyncInformation
import workbook.singletons.FileDataStorage
import workbook.user.User
case class FullInfo() extends HtmlAppElement {

  private lazy val domElement: Element = {
    val workbookSignal: Signal[Element] = signals.workbook.mapLazy {
      case Some(workbook) => workbook.loadedWorkbook.getDomElement()
      case None => div(text <-- signals.stringFromLanguageMapId("basic/noWorkbookLoaded"))
    }
    val withFullscreen: Signal[List[Element]] = workbookSignal.map(workbookDom => List(technical.fullScreenContainer.getDomElement(), workbookDom))
    div(children <-- withFullscreen)
  }

  override def getDomElement(): Element = domElement

  private val defaults: HomepageDefaults = HomepageDefaults()

  lazy val technical = TechnicalHomepageElements(HtmlFullScreenContainerElement(), FileDataStorage())

  private val defaultInfo = HomepageInfo(
    homepageDefaults = HomepageDefaults(),
    currentLanguage = defaults.defaultLanguage,
    workbookInfo = None,
    userInfo = None
  )

  private[info] val homepageInfoVar: Var[HomepageInfo] = Var(defaultInfo)

  /*  homepageInfoVar.signal.foreach(onNext => {
      println("Changed homepageInfoVar at: " + new Exception().getStackTrace().take(3).map(_.getMethodName).mkString(" -> ") + " (to: " + onNext.toString + ")")
    })(unsafeWindowOwner)*/

  def control: HomepageDataControl = HomepageDataControl(this)

  def signals: HomepageSignalInfo = HomepageSignalInfo(this)

  def current: HomepageCurrentInfo = HomepageCurrentInfo(this)


}

object FullInfo {

  private[info] case class HomepageDefaults() {
    val availableLanguages: List[HumanLanguage] = List(AppLanguage.German, AppLanguage.English)

    val defaultLanguage: HumanLanguage = AppLanguage.German

    val defaultSyncLocation: List[SyncInformation] = List(SyncInformation.syncEverythingToBrowser)

    val defaultUser: AllUserInfo = AllUserInfo(User("TestUser", "test@homepage"), UserConfig(defaultSyncLocation))
  }

  val singleton = FullInfo()

  def resetLocalStorage(): Unit = {
    val map = (0 until dom.window.localStorage.length)
      .flatMap { i =>
        Option(dom.window.localStorage.key(i)).flatMap { key =>
          Option(dom.window.localStorage.getItem(key)).map(value => key -> value)
        }
      }
      .toMap

    println("[WARN] resetting local storage in FullInfo! (CallStack: " + new Exception().getStackTrace().take(6).map(_.getMethodName).mkString(" -> ") + ")")

    map.keys.foreach(curKey => {
      println(curKey.toString + " -> " + map(curKey))
    })


    dom.window.localStorage.clear()
  }

  def setDummyUser(): Unit = {
    if (singleton.current.userInfo.isEmpty) {
      singleton.control.changeUser(Some(singleton.defaults.defaultUser))
    }
  }

}
