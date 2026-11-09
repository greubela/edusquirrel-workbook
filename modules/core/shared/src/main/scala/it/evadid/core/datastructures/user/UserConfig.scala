package it.evadid.core.datastructures.user

import it.evadid.workbook.interaction.sync.SyncInformation
import upickle.default.*

case class UserConfig(syncDestinations: List[SyncInformation], isOnlineAccount: Boolean) derives ReadWriter {
  def withoutCredentials: UserConfig = copy(syncDestinations = syncDestinations.map { info =>
    info.syncDestination match {
      case database: it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.SyncDestinationBasedOnHistoryDatabase =>
        info.copy(syncDestination = database.copy(databaseConfig = database.databaseConfig.withoutCredentials))
      case _ => info
    }
  })

  lazy val toJson: String = write(this)(using UserConfig.derived$ReadWriter)
}


object UserConfig {
  lazy val defaultSyncDestinations: List[SyncInformation] = {
    import it.evadid.workbook.interaction.sync.SyncStrategy.{SYNC_LAST_AND_MAJOR, SYNC_MAJOR}
    import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.*
    List(
      SyncInformation(defaultBrowserHistory, SYNC_LAST_AND_MAJOR),
      SyncInformation(defaultDatabaseWithKey, SYNC_MAJOR),
      SyncInformation(defaultDatabaseKeyless, SYNC_MAJOR),
      SyncInformation(defaultDatabaseV2, SYNC_MAJOR)
    )
  }

  def onlineDefaults: UserConfig = UserConfig(defaultSyncDestinations, isOnlineAccount = true)
}
