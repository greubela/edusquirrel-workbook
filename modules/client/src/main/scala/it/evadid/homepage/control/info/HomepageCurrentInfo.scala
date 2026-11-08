package it.evadid.homepage.control.info

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.user.AllUserInfo
import it.evadid.homepage.control.model.*
import it.evadid.homepage.workbook.syncDestination.{DatabaseSyncViaBackendServerHandler, LocalIndexedDbStorageSyncHandler, LocalStorageSyncHandler}
import it.evadid.workbook.abstractions.WorkbookInteractionUntyped
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.{LocalIndexedDbSyncDefinition, LocalStorageSyncDefinition, SyncDestinationBasedOnHistoryDatabase}
import it.evadid.workbook.interaction.sync.destination.{SyncDestinationDefinition, SyncDestinationHistoryHandler}
import it.evadid.workbook.interaction.sync.*

case class HomepageCurrentInfo(fullInfo: FullInfo) {

  private def now(): HomepageInfo = fullInfo.homepageInfoState.now()

  def workbookInfo: Option[AllWorkbookInfo] = fullInfo.synchronized {
    now().workbookInfo
  }




  def currentHomepageContext: UsageContext = now().toContext

  /*def workbookUserData: Option[WorkbookUserDataAnalyzer] = fullInfo.synchronized {
    if (userInfo.isEmpty || workbookInfo.isEmpty) None
    else Some(WorkbookUserDataAnalyzer(fullInfo.loggerSystemInfo.contentControlLogger, fullInfo.contentControl.downloadToDisc, now()))
  }*/

  def userInfo: Option[AllUserInfo] = fullInfo.synchronized {
    now().userInfo
  }

  def allAvailableInteractions: List[WorkbookInteractionUntyped] = fullInfo.synchronized {
    val default = List()
    now().workbookInfo.map(_.loadedWorkbook.allInteractionsInThisInstance).getOrElse(default)
  }

  def allAvailableLanguages: List[HumanLanguage] = fullInfo.synchronized {
    now().workbookInfo.map(_.loadedWorkbook.metadata.availableLanguages).getOrElse(List())
  }

}
