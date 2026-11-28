package it.evadid.homepage.control.change

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.user.{AllUserInfo, UserCredentials}
import it.evadid.homepage.control.model.{AllDisplayInfo, HomepageInfo}
import it.evadid.workbook.session.{AllWorkbookInfo, WorkbookConfig}

/** Every change of the page state: user, workbook, display, language and language maps. Each method returns the
  * state it sets, computed from the current one.
  */
final class HomepageControl(homepageInfoVar: Var[HomepageInfo]) {
  def now(): HomepageInfo = homepageInfoVar.now()
  def signal: StrictSignal[HomepageInfo] = homepageInfoVar.signal

  /** Updates the state with `transition`, which composes with other updates of the same Airstream transaction. */
  private def change(transition: HomepageInfo => HomepageInfo): HomepageInfo = {
    val expected = transition(now())
    homepageInfoVar.update(transition)
    expected
  }

  def setUser(user: Option[AllUserInfo]): HomepageInfo = change(_.copy(userInfo = user))

  def setCredentials(credentials: UserCredentials): HomepageInfo =
    change(info => info.copy(userInfo = info.userInfo.map(_.copy(credentials = credentials))))

  def setWorkbook(workbook: Option[AllWorkbookInfo]): HomepageInfo = change(_.copy(workbookInfo = workbook))

  def updateWorkbookConfig(transition: WorkbookConfig => WorkbookConfig): HomepageInfo =
    change(info => info.copy(workbookInfo = info.workbookInfo.map(workbook => workbook.copy(config = transition(workbook.config)))))

  def updateDisplay(transition: AllDisplayInfo => AllDisplayInfo): HomepageInfo =
    change(info => info.copy(displayInfo = transition(info.displayInfo)))

  def setLanguage(language: HumanLanguage): HomepageInfo = change(_.copy(currentLanguage = language))

  def updateLanguageMaps(transition: LanguageMapStorage => LanguageMapStorage): HomepageInfo =
    change(info => info.copy(languageMapStore = transition(info.languageMapStore)))
}
