package it.evadid.homepage.control.info

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.sync.SyncInformation.SyncInformationWithContext
import it.evadid.workbook.model.interaction.sync.{SyncInformation, UsageContext}

import it.evadid.homepage.control.change.HomepageDataControl.*
import it.evadid.homepage.control.change.*
import it.evadid.homepage.control.model.*
import it.evadid.homepage.control.singletons.*
case class HomepageCurrentInfo(fullInfo: FullInfo) {

  private def now(): HomepageInfo = fullInfo.homepageInfoState.now()

  def workbookInfo: Option[AllWorkbookInfo] = fullInfo.synchronized {
    now().workbookInfo
  }

  def currentSyncSources: List[SyncInformationWithContext] = fullInfo.synchronized {
    val curContext: UsageContext = fullInfo.homepageInfoState.now().toContext
    val syncInformation: List[SyncInformation] = userInfo.map(_.config.syncDestinations.toList).toList.flatten
    val syncWithContext: List[SyncInformationWithContext] = syncInformation.map(_.forContext(fullInfo.current.currentHomepageContext))
    println(s"currentSyncSources: ${syncWithContext.map(_.syncSource)}")
    syncWithContext
  }

  def currentHomepageContext: UsageContext = now().toContext

  def workbookUserData: Option[WorkbookUserDataAnalyzer] = fullInfo.synchronized {
    if (userInfo.isEmpty || workbookInfo.isEmpty) None
    else Some(WorkbookUserDataAnalyzer(fullInfo.technical, userInfo.get, workbookInfo.get))
  }

  def userInfo: Option[AllUserInfo] = fullInfo.synchronized {
    now().userInfo
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
