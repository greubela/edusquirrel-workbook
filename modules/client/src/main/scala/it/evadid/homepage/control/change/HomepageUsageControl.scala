package it.evadid.homepage.control.change

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.control.model.*
import it.evadid.homepage.workbook.content.WorkbookFactory
import it.evadid.workbook.abstractions.WorkbookInteractionElement

import scala.concurrent.*

case class HomepageUsageControl(fullInfo: FullInfo) {

  private given ExecutionContext = ExecutionContext.global

  private def interactions: List[WorkbookInteractionElement[?]] = fullInfo.current.allAvailableInteractions

  def changeDisplay(func: AllDisplayInfo => AllDisplayInfo): Unit = fullInfo.synchronized {
    updateInfoWithoutContextChange((curInfo: HomepageInfo) => curInfo.copy(displayInfo = func(curInfo.displayInfo)))
  }

  def updateInfoWithContextChange(func: HomepageInfo => HomepageInfo): Future[?] = fullInfo.synchronized {

    fullInfo.syncControl
      .storeAndReset(interactions.map(_.interactionVariable))
      .flatMap(_ => {
        fullInfo.homepageInfoState.update(func)
        fullInfo.syncControl.ensureFetchAndLoad(interactions.map(_.interactionVariable))
      })

  }

  private[change] def updateInfoWithoutContextChange(func: HomepageInfo => HomepageInfo): Future[?] = fullInfo.synchronized {
    fullInfo.syncControl.ensureFetchAndLoad(interactions.map(_.interactionVariable)).map(_ => {
      fullInfo.homepageInfoState.update(func)
    })
  }

  def changeWorkbook(factory: WorkbookFactory): Unit = fullInfo.synchronized {
    changeWorkbook(factory.createEverything)
  }

  def changeWorkbook(newWorkbook: AllWorkbookInfo): Unit = fullInfo.synchronized {
    //saveAndResetAllInfo()
    updateInfoWithContextChange(_.copy(workbookInfo = Some(newWorkbook)))
  }

  def updateWorkbookConfig(func: WorkbookConfig => WorkbookConfig): Unit = fullInfo.synchronized {
    if (fullInfo.homepageInfoState.now().workbookInfo.nonEmpty) {
      val newInfo: Option[AllWorkbookInfo] = fullInfo.homepageInfoState.now().workbookInfo.map(info => info.copy(config = func(info.config)))
      updateInfoWithoutContextChange(curInfo => curInfo.copy(workbookInfo = newInfo))
      //cacheControl.fetchAndStore(interactions.map(_.interactionVariable))
    } else {
      fullInfo.loggerSystemInfo.contentControlLogger.logWarn("[WARN] ignore updated workbook config because there is no workbook loaded!")
    }

  }

  def changeUser(userInfo: Option[AllUserInfo]): Unit = fullInfo.synchronized {
    updateInfoWithContextChange(_.copy(userInfo = userInfo))
  }

  def changeLanguage(language: HumanLanguage): Unit = fullInfo.synchronized {
    fullInfo.homepageInfoState.update(_.copy(currentLanguage = language))
  }


}

object HomepageUsageControl {


}