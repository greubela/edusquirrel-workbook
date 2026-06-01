package it.evadid.homepage.control.info.control

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.control.info.{AllUserInfo, AllWorkbookInfo, FullInfo, HomepageInfo}
import it.evadid.homepage.control.info.analyze.WorkbookUserDataAnalyzer
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.sync.SyncInformation
case class HomepageCurrentInfo(fullInfo: FullInfo) {

  private def now(): HomepageInfo = fullInfo.homepageInfoState.now()
  
  def workbookInfo: Option[AllWorkbookInfo] = fullInfo.synchronized {
    now().workbookInfo
  }

  def workbookUserData: Option[WorkbookUserDataAnalyzer] = fullInfo.synchronized {
    if (userInfo.isEmpty || workbookInfo.isEmpty) None
    else Some(WorkbookUserDataAnalyzer(fullInfo.technical, userInfo.get, workbookInfo.get))
  }

  def userInfo: Option[AllUserInfo] = fullInfo.synchronized {
    now().userInfo
  }

  def allSyncSources: List[SyncInformation] = fullInfo.synchronized {
    val default = now().homepageDefaults.defaultSyncLocation
    now().userInfo.map(_.config.syncDestinations).getOrElse(default)
  }

  def allAvailableInteractions: List[WorkbookInteraction[?]] = fullInfo.synchronized {
    val default = List()
    now().workbookInfo.map(_.loadedWorkbook.allContainedInteractions).getOrElse(default)
  }

  def allAvailableLanguages: List[HumanLanguage] = fullInfo.synchronized {
    val default = now().homepageDefaults.availableLanguages
    now().workbookInfo.map(_.loadedWorkbook.availableLanguages).getOrElse(default)
  }

}
