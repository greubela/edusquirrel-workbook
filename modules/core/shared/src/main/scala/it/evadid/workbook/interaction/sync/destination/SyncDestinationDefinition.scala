package it.evadid.workbook.interaction.sync.destination

import it.evadid.sql.DatabaseConfiguration.PartialDatabaseInformation
import it.evadid.sql.DatabaseHistoryFormat
import upickle.default.*

sealed trait SyncDestinationDefinition derives ReadWriter {

  def isRemote: Boolean = this match {
    case _: SyncDestinationDefinition.SyncDestinationBasedOnHistoryDatabase => true
    case _: SyncDestinationDefinition.LocalIndexedDbSyncDefinition => false
    case _: SyncDestinationDefinition.LocalStorageSyncDefinition => false
  }

}

object SyncDestinationDefinition {

  case class SyncDestinationBasedOnHistoryDatabase(databaseConfig: PartialDatabaseInformation, format: DatabaseHistoryFormat) extends SyncDestinationDefinition derives ReadWriter {

    override def toString: String =
      s"DatabaseDestination(${databaseConfig.hostOp.getOrElse("defaultRemoteHost")}->${databaseConfig.databaseOp.getOrElse("defaultRemoteDb")}/${format.tables.mkString(",")})"
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
    SyncDestinationBasedOnHistoryDatabase(it.evadid.sql.DatabaseConfiguration.defaultDb, DatabaseHistoryFormat.ClassicJsonWithKey)

  val defaultDatabaseKeyless: SyncDestinationBasedOnHistoryDatabase =
    SyncDestinationBasedOnHistoryDatabase(it.evadid.sql.DatabaseConfiguration.defaultDb, DatabaseHistoryFormat.ClassicKeylessJson)

  val defaultDatabaseV2: SyncDestinationBasedOnHistoryDatabase =
    SyncDestinationBasedOnHistoryDatabase(it.evadid.sql.DatabaseConfiguration.defaultDb, DatabaseHistoryFormat.RelationalEvents)

}

