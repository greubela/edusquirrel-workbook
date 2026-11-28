package it.evadid.homepage.control.change

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.user.{AllUserInfo, UserCredentials}
import it.evadid.homepage.control.model.{AllDisplayInfo, HomepageInfo}
import it.evadid.util.logging.Logger
import it.evadid.workbook.session.{AllWorkbookInfo, WorkbookConfig}

import scala.util.Try

/** Every change of the page state: user, workbook, display, language and language maps. Each change is a
  * transition of the current state, so changes made in one Airstream transaction compose.
  */
final class HomepageControl(homepageInfoVar: Var[HomepageInfo], logger: Logger) {
  def now(): HomepageInfo = homepageInfoVar.now()
  def signal: StrictSignal[HomepageInfo] = homepageInfoVar.signal

  /** Applies `transition` and passes the changed page to `andThen` in the same step, so it sees exactly that page even
    * when several changes are queued in one Airstream transaction.
    */
  def change(transition: HomepageInfo => HomepageInfo, andThen: HomepageInfo => Unit = _ => ()): Unit =
    homepageInfoVar.update { current =>
      val next = transition(current)
      Try(andThen(next)).failed.foreach(error => logger.logExceptionWarn("Could not follow a page change", error))
      next
    }

  def setUser(user: Option[AllUserInfo]): Unit = change(HomepageControl.withUser(user))
  def setCredentials(credentials: UserCredentials): Unit = change(HomepageControl.withCredentials(credentials))
  def setWorkbook(workbook: Option[AllWorkbookInfo]): Unit = change(HomepageControl.withWorkbook(workbook))

  def updateWorkbookConfig(transition: WorkbookConfig => WorkbookConfig): Unit =
    change(info => info.copy(workbookInfo = info.workbookInfo.map(workbook => workbook.copy(config = transition(workbook.config)))))

  def updateDisplay(transition: AllDisplayInfo => AllDisplayInfo): Unit = change(info => info.copy(displayInfo = transition(info.displayInfo)))

  def setLanguage(language: HumanLanguage): Unit = change(_.copy(currentLanguage = language))

  def updateLanguageMaps(transition: LanguageMapStorage => LanguageMapStorage): Unit =
    change(info => info.copy(languageMapStore = transition(info.languageMapStore)))
}

object HomepageControl {
  def withUser(user: Option[AllUserInfo]): HomepageInfo => HomepageInfo = _.copy(userInfo = user)
  def withCredentials(credentials: UserCredentials): HomepageInfo => HomepageInfo =
    info => info.copy(userInfo = info.userInfo.map(_.copy(credentials = credentials)))
  def withWorkbook(workbook: Option[AllWorkbookInfo]): HomepageInfo => HomepageInfo = _.copy(workbookInfo = workbook)
}
