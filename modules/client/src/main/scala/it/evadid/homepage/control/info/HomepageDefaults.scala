package it.evadid.homepage.control.info

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.workbook.legacy.model.interaction.sync.{DatabaseSyncViaBackendServer, DatabaseSyncViaTmpMathBackend, LocalStorageSync}
import it.evadid.workbook.model.interaction.sync.*
import it.evadid.workbook.model.interaction.sync.SyncStrategy.{SYNC_EVERYTHING, SYNC_MAJOR}

case class HomepageDefaults() {
  val availableLanguages: List[HumanLanguage] = List(AppLanguage.German, AppLanguage.English)

  val defaultLanguage: HumanLanguage = AppLanguage.German

  val defaultSyncLocation: List[SyncInformation] = List(
    SyncInformation(LocalStorageSync, SyncStrategy.SYNC_EVERYTHING),
    SyncInformation(DatabaseSyncViaBackendServer, SYNC_MAJOR)
    //SyncInformation(DatabaseSyncViaTmpMathBackend, SYNC_EVERYTHING)
  )

  val defaultUserConfig: UserConfig = UserConfig(
    defaultSyncLocation
  )

  val defaultUser: AllUserInfo = createDefaultUserInfo(User("TestUser", "test@homepage"))

  val selectableUsers: List[User] = List(
    User("André", "andre@homepage"),
    User("TestUser", "test@homepage"),
    User("TestStudent", "test-student@homepage"),
    User("Bettina", "bettina@homepage")
  )

  def createDefaultUserInfo(user: User): AllUserInfo = AllUserInfo(user, defaultUserConfig)
}
