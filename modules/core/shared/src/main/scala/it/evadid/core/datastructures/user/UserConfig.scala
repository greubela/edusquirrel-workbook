package it.evadid.core.datastructures.user

import it.evadid.workbook.interaction.sync.SyncInformation
import it.evadid.workbook.interaction.sync.SyncStrategy.*
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.*
import upickle.default.*

case class UserConfig(syncDestinations: List[SyncInformation], isOnlineAccount: Boolean) derives ReadWriter {
  def withoutCredentials: UserConfig = copy(syncDestinations = syncDestinations.map(_.withoutCredentials))

  lazy val toJson: String = write(this)(using UserConfig.derived$ReadWriter)
}


object UserConfig {
  lazy val defaultSyncDestinations: List[SyncInformation] = {
    List(
      SyncInformation(defaultBrowserHistory, SYNC_LAST_AND_MAJOR),
      SyncInformation(defaultDatabaseWithKey, SYNC_MAJOR),
      SyncInformation(defaultDatabaseKeyless, SYNC_MAJOR),
      SyncInformation(defaultDatabaseV2, SYNC_LAST_FIVE)
    )
  }

  def onlineDefaults: UserConfig = UserConfig(defaultSyncDestinations, isOnlineAccount = true)

}
