package workbook.model.info

import workbook.model.interaction.sync.SyncInformation

case class UserConfig(syncDestinations: List[SyncInformation]) {

}

object UserConfig {

  val defaultConfig: UserConfig = UserConfig(List(SyncInformation.syncEverythingToBrowser))

}

