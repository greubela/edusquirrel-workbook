package it.evadid.homepage.control.info

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.user.User
import it.evadid.executors.MathExecutor
import it.evadid.homepage.workbook.legacy.model.interaction.sync.LocalStorageSync
import it.evadid.workbook.model.interaction.sync.*

case class HomepageDefaults() {
  val availableLanguages: List[HumanLanguage] = List(AppLanguage.German, AppLanguage.English)

  val defaultLanguage: HumanLanguage = AppLanguage.German

  val defaultSyncLocation: List[SyncInformation] = List(
    SyncInformation(LocalStorageSync, SyncStrategy.SYNC_EVERYTHING)
  )

  val defaultUser: AllUserInfo = AllUserInfo(User("TestUser", "test@homepage"), UserConfig(defaultSyncLocation))
  
  val defaultUserConfig: UserConfig = UserConfig(defaultSyncLocation)
}