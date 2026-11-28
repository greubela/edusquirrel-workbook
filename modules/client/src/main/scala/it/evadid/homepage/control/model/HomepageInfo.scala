package it.evadid.homepage.control.model

import it.evadid.workbook.session.{AllWorkbookInfo, SessionData}
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig}
import it.evadid.homepage.workbook.syncDestination.{DatabaseSyncViaBackendServerHandler, LocalIndexedDbStorageSyncHandler, LocalStorageSyncHandler}
import it.evadid.workbook.sync.destination.SyncDestinationDefinition.{LocalIndexedDbSyncDefinition, LocalStorageSyncDefinition, SyncDestinationBasedOnHistoryDatabase}
import it.evadid.workbook.sync.destination.{SyncDestinationDefinition, SyncDestinationHistoryHandler}
import it.evadid.workbook.sync.{SyncInformationWithContext, UsageContext}
import it.evadid.workbook.model.InteractionInfo
import it.evadid.workbook.sync.model.InteractionHistoryJson

import java.time.LocalDateTime


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

  /** The open session without credentials, with each of `answers`' interaction's format-coded history; `None` without a workbook or user. */
  def toSessionData(answers: InteractionInfo): Option[SessionData] = for {
    workbook <- workbookInfo
    user <- userInfo.map(_.withoutCredentials())
  } yield sessionOf(workbook, user.user, user.config, answers)

  /** The open workbook handed in by `submitter` instead of the bound user; `None` without a workbook. Every current
    * history is kept under its bound context, and the configuration is local-only, so continuing the copy never
    * syncs to or creates an account in a database. The bound user and its bindings are not changed.
    */
  def toSubmissionBy(submitter: User, answers: InteractionInfo): Option[SessionData] =
    workbookInfo.map(sessionOf(_, submitter, UserConfig.defaults(isLocalAccount = true), answers))

  private def sessionOf(workbook: AllWorkbookInfo, user: User, config: UserConfig, answers: InteractionInfo): SessionData = {
    val histories = workbook.loadedWorkbook.allContainedInteractionsById.values.map { interaction =>
      toContext.toSyncContext(interaction.keyForSerialization) ->
        InteractionHistoryJson.fromHistory(interaction.interactionFormat, answers.historyOf(interaction))
    }.toMap
    SessionData(LocalDateTime.now(), histories, user, Some(config), workbook)
  }

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


