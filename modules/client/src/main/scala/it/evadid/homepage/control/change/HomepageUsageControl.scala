package it.evadid.homepage.control.change

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.control.model.*
import it.evadid.homepage.workbook.content.WorkbookFactory
import it.evadid.workbook.model.abstractions.WorkbookInteractionElement

import java.time.LocalDateTime
import scala.concurrent.*

case class HomepageUsageControl(fullInfo: FullInfo) {


  private given ExecutionContext = ExecutionContext.global

  private def interactions: List[WorkbookInteractionElement[?]] = fullInfo.current.allAvailableInteractions

  private def cacheControl = fullInfo.cacheControl

  def changeDisplay(func: AllDisplayInfo => AllDisplayInfo): Unit = fullInfo.synchronized {
    updateInfoWithoutContextChange((curInfo: HomepageInfo) => curInfo.copy(displayInfo = func(curInfo.displayInfo)))
  }

  private[change] def updateInfoWithoutContextChange(func: HomepageInfo => HomepageInfo): Future[?] = fullInfo.synchronized {
    Future.successful(fullInfo.homepageInfoState.update(func))
  }

  private[change] def updateInfoChangingContext(func: HomepageInfo => HomepageInfo): Future[?] = fullInfo.synchronized {
    def beforeContextChanged(): Future[?] = {
      //cacheControl.downloadAllAvailableData()
      cacheControl.requestStore(interactions.map(_.interactionVariable), true, LocalDateTime.now())
    }

    def afterContextChange(): Future[?] = Future {
      val maxTime: LocalDateTime = LocalDateTime.now()
      interactions.foreach(_.interactionVariable.resetHistoryAndSyncControl(Some(cacheControl)))
      cacheControl.fetchAndStore(interactions.map(_.interactionVariable))
    }

    beforeContextChanged().flatMap(res1 => {
      Future.traverse(fullInfo.current.currentSyncSources)(_.informAboutContextSwitch()).flatMap(res2 => {
        fullInfo.homepageInfoState.update(func)
        afterContextChange()
      })
    })
  }


  def changeWorkbook(factory: WorkbookFactory): Unit = fullInfo.synchronized {
    changeWorkbook(factory.createEverything)
  }

  def changeWorkbook(newWorkbook: AllWorkbookInfo): Unit = fullInfo.synchronized {
    //saveAndResetAllInfo()
    updateInfoChangingContext(_.copy(workbookInfo = Some(newWorkbook)))
  }

  def updateWorkbookConfig(func: WorkbookConfig => WorkbookConfig): Unit = fullInfo.synchronized {
    if (fullInfo.homepageInfoState.now().workbookInfo.nonEmpty) {
      val newInfo: Option[AllWorkbookInfo] = fullInfo.homepageInfoState.now().workbookInfo.map(info => info.copy(config = func(info.config)))
      updateInfoChangingContext(curInfo => curInfo.copy(workbookInfo = newInfo))
      cacheControl.fetchAndStore(interactions.map(_.interactionVariable))
    } else {
      fullInfo.loggerSystemInfo.workbookControlLogger.logWarn("[WARN] ignore updated workbook config because there is no workbook loaded!")
    }

  }

  def changeUser(userInfo: Option[AllUserInfo]): Unit = fullInfo.synchronized {
    updateInfoChangingContext(_.copy(userInfo = userInfo))
  }

  def changeLanguage(language: HumanLanguage): Unit = fullInfo.synchronized {
    fullInfo.homepageInfoState.update(_.copy(currentLanguage = language))
  }


}

object HomepageUsageControl {


}