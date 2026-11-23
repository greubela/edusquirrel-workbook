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
  /** The one definition of default destinations. Local users cannot sign in to the backend, so they keep their
    * answers only in the browser; online accounts also send committed (non-TEMPORARY) answers to both backend layouts.
    */
  def defaultSyncDestinations(isLocalAccount: Boolean): Seq[SyncInformation] =
    SyncInformation(defaultBrowserHistory, SYNC_LAST) +: (if isLocalAccount then Seq.empty else Seq(
      SyncInformation(defaultDatabase, SYNC_MAJOR),
      SyncInformation(defaultBlobDatabase, SYNC_MAJOR)))

  def defaults(isLocalAccount: Boolean): UserConfig =
    UserConfig(defaultSyncDestinations(isLocalAccount), isOnlineAccount = !isLocalAccount)

}
