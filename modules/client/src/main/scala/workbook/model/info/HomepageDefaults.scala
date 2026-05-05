package workbook.model.info

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import workbook.model.interaction.sync.SyncInformation
import workbook.user.User

case class HomepageDefaults() {
  val availableLanguages: List[HumanLanguage] = List(AppLanguage.German, AppLanguage.English)

  val defaultLanguage: HumanLanguage = AppLanguage.German

  val defaultSyncLocation: List[SyncInformation] = List(SyncInformation.syncEverythingToBrowser)

  val defaultUser: AllUserInfo = AllUserInfo(User("TestUser", "test@homepage"), UserConfig(defaultSyncLocation))
}