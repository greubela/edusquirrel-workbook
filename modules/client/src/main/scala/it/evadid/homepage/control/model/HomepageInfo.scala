package it.evadid.homepage.control.model

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.user.AllUserInfo
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

  def syncDestinations(fullInfo: FullInfo): Seq[SyncInformationWithContext] = {
    userInfo.toList.flatMap(curUserInfo => curUserInfo.config.syncDestinations.map(_.forContext(toContext, dest => resolveSyncDefinition(dest, fullInfo))))
  }

  private def resolveSyncDefinition(dest: SyncDestinationDefinition, fullInfo: FullInfo): SyncDestinationHistoryHandler = dest.match{
    case db: SyncDestinationBasedOnHistoryDatabase =>
      val backend = fullInfo.defaults.backendExecutorWithCredentials(userInfo.flatMap(_.credentials.signedBackendToken))
      val database = userInfo.flatMap(_.credentials.databaseConfig).getOrElse(db.databaseConfig)
      val handler = DatabaseSyncViaBackendServerHandler(fullInfo.loggerSystemInfo.syncControlLogger, db, backend, database)
      if db.buffered then handler.toBuffered else handler
    case li: LocalIndexedDbSyncDefinition => LocalIndexedDbStorageSyncHandler(li).withSyncLogger(fullInfo.loggerSystemInfo.syncControlLogger).forHistory()
    case ls: LocalStorageSyncDefinition => LocalStorageSyncHandler(ls).withSyncLogger(fullInfo.loggerSystemInfo.syncControlLogger).forHistory()
  }

}

object HomepageInfo {


}


