package it.evadid.core.datastructures.user

import it.evadid.workbook.interaction.sync.SyncInformation
import it.evadid.workbook.interaction.sync.EventSyncStrategy.*
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.*
import upickle.default.*

case class UserConfig(syncDestinations: Seq[SyncInformation], isOnlineAccount: Boolean) derives ReadWriter {
  def withoutCredentials: UserConfig = copy(syncDestinations = syncDestinations.map(_.withoutCredentials))

  lazy val toJson: String = write(this)(using UserConfig.derived$ReadWriter)
}


object UserConfig {
  lazy val defaultSyncDestinations: Seq[SyncInformation] = {
    List(
      // Local storage keeps the current answer; remote destinations receive committed (non-TEMPORARY) answers.
      SyncInformation(defaultBrowserHistory, SYNC_LAST),
      SyncInformation(defaultDatabase, SYNC_MAJOR),
      SyncInformation(defaultBlobDatabase, SYNC_MAJOR)
    )
  }

  def onlineDefaults: UserConfig = UserConfig(defaultSyncDestinations, isOnlineAccount = true)

}
