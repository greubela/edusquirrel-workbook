package it.evadid.core.datastructures.user

import it.evadid.workbook.interaction.sync.SyncInformation
import upickle.default.*

case class UserConfig(syncDestinations: List[SyncInformation], isOnlineAccount: Boolean) derives ReadWriter {
  lazy val toJson: String = write(this)(using UserConfig.derived$ReadWriter)
}


object UserConfig {



  lazy val defaultSyncDestinations: List[SyncInformation] = {
    import it.evadid.sql.DatabaseConfiguration.PartialDatabaseInformation
    import it.evadid.sql.DatabaseHistoryFormat.{ClassicJsonWithKey, ClassicKeylessJson}
    import it.evadid.workbook.interaction.sync.SyncStrategy.{SYNC_LAST_AND_MAJOR, SYNC_MAJOR}
    import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.*
    List(
      SyncInformation(LocalIndexedDbSyncDefinition("EvaDidInteractionDB", "variableHistoryStore", 1), SYNC_LAST_AND_MAJOR),
      SyncInformation(SyncDestinationBasedOnHistoryDatabase(it.evadid.sql.DatabaseConfiguration.defaultDb, ClassicJsonWithKey), SYNC_MAJOR),
      SyncInformation(SyncDestinationBasedOnHistoryDatabase(it.evadid.sql.DatabaseConfiguration.defaultDb, ClassicKeylessJson), SYNC_MAJOR)
    )
  }

  def onlineDefaults: UserConfig = UserConfig(defaultSyncDestinations, isOnlineAccount = true)
}
