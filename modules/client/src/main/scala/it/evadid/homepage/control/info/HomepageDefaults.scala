package it.evadid.homepage.control.info

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.workbook.legacy.model.interaction.sync.{DatabaseSyncViaBackendServer, DatabaseSyncViaTmpMathBackend, LocalStorageSync}
import it.evadid.workbook.model.interaction.sync.*
import it.evadid.workbook.model.interaction.sync.SyncStrategy.{SYNC_EVERYTHING, SYNC_MAJOR}

case class HomepageDefaults() {
  lazy val availableLanguages: List[HumanLanguage] = List(AppLanguage.German, AppLanguage.English)

  lazy val defaultLanguage: HumanLanguage = AppLanguage.German

  lazy val defaultSyncLocation: List[SyncInformation] = List(
    SyncInformation(LocalStorageSync, SyncStrategy.SYNC_EVERYTHING),
    SyncInformation(DatabaseSyncViaBackendServer, SYNC_MAJOR)
  )

  lazy val defaultUser: AllUserInfo = selectableUsers.head

  lazy val defaultUserConfig: UserConfig = UserConfig(
    defaultSyncLocation
  )

  lazy val selectableUsers: List[AllUserInfo] = List(
    User("André", "andre@homepage"),
    User("TestUser", "test@homepage"),
    User("TestStudent", "test-student@homepage"),
    User("Bettina", "bettina@homepage")
  ).map(createDefaultUserInfo)

  def createDefaultUserInfo(user: User): AllUserInfo = AllUserInfo(user, defaultUserConfig)
}
