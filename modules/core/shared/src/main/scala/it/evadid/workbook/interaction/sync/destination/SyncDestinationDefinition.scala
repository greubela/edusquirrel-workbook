package it.evadid.workbook.interaction.sync.destination

import it.evadid.sql.DatabaseConfig.DatabaseConfigPartialInformation
import it.evadid.sql.DatabaseHistoryFormat
import upickle.default.*

sealed trait SyncDestinationDefinition derives ReadWriter {

}

object SyncDestinationDefinition {

  case class SyncDestinationBasedOnConfigMap(instanceTypeToCreate: String, config: Map[String, String]) extends SyncDestinationDefinition derives ReadWriter

  case class SyncDestinationBasedOnHistoryDatabase(databaseConfig: DatabaseConfigPartialInformation, format: DatabaseHistoryFormat) extends SyncDestinationDefinition derives ReadWriter

  case class LocalIndexedDbSyncDefinition(dbName: String, storeName: String, dbVersion: Int) extends SyncDestinationDefinition derives ReadWriter

  case class LocalStorageSyncDefinition(maxValueCharakterSize: Int) extends SyncDestinationDefinition derives ReadWriter

}

