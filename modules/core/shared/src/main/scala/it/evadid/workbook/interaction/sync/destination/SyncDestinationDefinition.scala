package it.evadid.workbook.interaction.sync.destination

import it.evadid.sql.{DatabaseConfiguration, DatabaseHistoryFormat}
import upickle.default.*

sealed trait SyncDestinationDefinition derives ReadWriter {

  def isRemote: Boolean = this match {
    case _: SyncDestinationDefinition.SyncDestinationBasedOnHistoryDatabase => true
    case _: SyncDestinationDefinition.LocalIndexedDbSyncDefinition => false
    case _: SyncDestinationDefinition.LocalStorageSyncDefinition => false
  }

  def withoutCredentials: SyncDestinationDefinition = this match {
    case db: SyncDestinationDefinition.SyncDestinationBasedOnHistoryDatabase =>
      db.copy(databaseConfig = db.databaseConfig.withoutCredentials)
    case other => other
  }

}

object SyncDestinationDefinition {

  case class SyncDestinationBasedOnHistoryDatabase(databaseConfig: DatabaseConfiguration, format: DatabaseHistoryFormat) extends SyncDestinationDefinition derives ReadWriter {

    override def toString: String =
      s"DatabaseDestination(${databaseConfig.host}->${databaseConfig.database}/${format.tables.mkString(",")})"
  }

  case class LocalIndexedDbSyncDefinition(dbName: String, storeName: String, dbVersion: Int) extends SyncDestinationDefinition derives ReadWriter {
    override def toString: String = s"DatabaseDestination(browser/IndexedDb->${dbName}/${storeName})"
  }

  case class LocalStorageSyncDefinition(maxValueCharakterSize: Int) extends SyncDestinationDefinition derives ReadWriter {
    override def toString: String = "DatabaseDestination(browser/localStorage->default)"
  }

  val defaultBrowserHistory: LocalIndexedDbSyncDefinition =
    LocalIndexedDbSyncDefinition("EvaDidInteractionDB", "variableHistoryStore", 1)

  val defaultDatabaseWithKey: SyncDestinationBasedOnHistoryDatabase =
    SyncDestinationBasedOnHistoryDatabase(it.evadid.sql.DatabaseConfig.defaultDb, DatabaseHistoryFormat.ClassicJsonWithKey)

  val defaultDatabaseKeyless: SyncDestinationBasedOnHistoryDatabase =
    SyncDestinationBasedOnHistoryDatabase(it.evadid.sql.DatabaseConfig.defaultDb, DatabaseHistoryFormat.ClassicKeylessJson)

  val defaultDatabaseV2: SyncDestinationBasedOnHistoryDatabase =
    SyncDestinationBasedOnHistoryDatabase(it.evadid.sql.DatabaseConfig.defaultDb, DatabaseHistoryFormat.RelationalEvents)

}

