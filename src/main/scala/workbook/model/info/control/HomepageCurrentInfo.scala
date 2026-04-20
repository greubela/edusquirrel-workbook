package workbook.model.info.control

import datastructures.core.language.HumanLanguage
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.info.*
import workbook.model.info.analyze.WorkbookUserDataAnalyzer
import workbook.model.interaction.sync.SyncInformation

case class HomepageCurrentInfo(fullInfo: FullInfo) {

  private def now(): HomepageInfo = fullInfo.homepageInfoVar.now()


  def workbookInfo: Option[AllWorkbookInfo] = fullInfo.synchronized {
    now().workbookInfo
  }

  def workbookUserData: Option[WorkbookUserDataAnalyzer] = fullInfo.synchronized {
    if (userInfo.isEmpty || workbookInfo.isEmpty) None
    else Some(WorkbookUserDataAnalyzer(userInfo.get, workbookInfo.get))
  }

  def userInfo: Option[AllUserInfo] = fullInfo.synchronized {
    now().userInfo
  }

  def allSyncSources: List[SyncInformation] = fullInfo.synchronized {
    val default = now().homepageDefaults.defaultSyncLocation
    now().userInfo.map(_.config.syncDestinations).getOrElse(default)
  }

  def allAvailableInteractions: List[WorkbookInteraction[_]] = fullInfo.synchronized {
    val default = List()
    now().workbookInfo.map(_.loadedWorkbook.allInteractionElements).getOrElse(default)
  }

  def allAvailableLanguages: List[HumanLanguage] = fullInfo.synchronized {
    val default = now().homepageDefaults.availableLanguages
    now().workbookInfo.map(_.loadedWorkbook.availableInLanguages).getOrElse(default)
  }

}
