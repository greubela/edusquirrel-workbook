package it.evadid.homepage.control.model

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.user.AllUserInfo
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.workbook.syncDestination.{DatabaseSyncViaBackendServerHandler, LocalIndexedDbStorageSyncHandler, LocalStorageSyncHandler}
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.{LocalIndexedDbSyncDefinition, LocalStorageSyncDefinition, SyncDestinationBasedOnHistoryDatabase}
import it.evadid.workbook.interaction.sync.destination.{SyncDestinationDefinition, SyncDestinationHistoryHandler}
import it.evadid.workbook.interaction.sync.{SyncInformationWithContext, UsageContext}


case class HomepageInfo(
                         //private[control] val homepageDefaults: HomepageDefaults,
                         currentLanguage: HumanLanguage,
                         workbookInfo: Option[AllWorkbookInfo],
                         userInfo: Option[AllUserInfo],
                         displayInfo: AllDisplayInfo,
                         languageMapStore: LanguageMapStorage,
                       ) {


  override val toString: String = s"HomepageInfo(userInfo: $userInfo, workbookInfo: $workbookInfo)"

  lazy val toContext = UsageContext("edusquirrel", workbookInfo.map(_.loadedWorkbook.workbookId).getOrElse("[no workbook]"), userInfo.map(_.user.personId).getOrElse("[no user]"))

  lazy val syncDestinations: Seq[SyncInformationWithContext] = {
    userInfo.toList.flatMap(curUserInfo => curUserInfo.config.syncDestinations.map(_.forContext(toContext, resolveSyncDefinition)))
  }

  private def resolveSyncDefinition(dest: SyncDestinationDefinition): SyncDestinationHistoryHandler = dest.match{
    case db: SyncDestinationBasedOnHistoryDatabase => DatabaseSyncViaBackendServerHandler(fullInfo.syncControl.syncLogger, db, fullInfo)
    case li: LocalIndexedDbSyncDefinition => LocalIndexedDbStorageSyncHandler(li).withSyncLogger(fullInfo.syncControl.syncLogger).forHistory()
    case ls: LocalStorageSyncDefinition => LocalStorageSyncHandler(ls).withSyncLogger(fullInfo.syncControl.syncLogger).forHistory()
    case _ => ???
  }

}

object HomepageInfo {


}


