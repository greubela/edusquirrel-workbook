package it.evadid.core.datastructures.user

import it.evadid.workbook.interaction.sync.SyncInformation
import upickle.default.*

case class UserConfig(syncDestinations: List[SyncInformation], isOnlineAccount: Boolean) derives ReadWriter {

}

object UserConfig {


}

