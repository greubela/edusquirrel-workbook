package it.evadid.homepage.control.change

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.user.{AllUserInfo, UserCredentials}
import it.evadid.homepage.control.model.{AllDisplayInfo, HomepageInfo}
import it.evadid.workbook.session.{AllWorkbookInfo, WorkbookConfig}

/** Every change of the page state: user, workbook, display, language and language maps. Each change is a
  * transition of the current state, so changes made in one Airstream transaction compose.
  */
final class HomepageControl(homepageInfoVar: Var[HomepageInfo]) {
  def now(): HomepageInfo = homepageInfoVar.now()
  def signal: StrictSignal[HomepageInfo] = homepageInfoVar.signal

  private def change(transition: HomepageInfo => HomepageInfo): Unit = homepageInfoVar.update(transition)

  def setUser(user: Option[AllUserInfo]): Unit = change(_.copy(userInfo = user))

  def setCredentials(credentials: UserCredentials): Unit =
    change(info => info.copy(userInfo = info.userInfo.map(_.copy(credentials = credentials))))

  def setWorkbook(workbook: Option[AllWorkbookInfo]): Unit = change(_.copy(workbookInfo = workbook))

  def updateWorkbookConfig(transition: WorkbookConfig => WorkbookConfig): Unit =
    change(info => info.copy(workbookInfo = info.workbookInfo.map(workbook => workbook.copy(config = transition(workbook.config)))))

  def updateDisplay(transition: AllDisplayInfo => AllDisplayInfo): Unit =
    change(info => info.copy(displayInfo = transition(info.displayInfo)))

  def setLanguage(language: HumanLanguage): Unit = change(_.copy(currentLanguage = language))

  def updateLanguageMaps(transition: LanguageMapStorage => LanguageMapStorage): Unit =
    change(info => info.copy(languageMapStore = transition(info.languageMapStore)))
}
