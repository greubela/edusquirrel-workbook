package it.evadid.core.datastructures.user

import it.evadid.workbook.interaction.sync.SyncInformation
import it.evadid.workbook.interaction.sync.EventSyncStrategy.*
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.*
import upickle.default.*

case class UserConfig(syncDestinations: List[SyncInformation], isOnlineAccount: Boolean) derives ReadWriter {
  def withoutCredentials: UserConfig = copy(syncDestinations = syncDestinations.map(_.withoutCredentials))

  lazy val toJson: String = write(this)(using UserConfig.derived$ReadWriter)
}


object UserConfig {
  lazy val defaultSyncDestinations: List[SyncInformation] = {
    List(
      SyncInformation(defaultBrowserHistory, KEEP_LAST_AND_MAJOR),
      SyncInformation(defaultDatabase, KEEP_LAST_FIVE)
    )
  }

  def onlineDefaults: UserConfig = UserConfig(defaultSyncDestinations, isOnlineAccount = true)

}
