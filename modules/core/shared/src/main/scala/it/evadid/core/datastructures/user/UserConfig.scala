package it.evadid.core.datastructures.user

import it.evadid.workbook.interaction.sync.SyncInformation

case class UserConfig(syncDestinations: List[SyncInformation], isOnlineAccount: Boolean) {

}

object UserConfig {


}

