package it.evadid.workbook.interaction.sync.destination

import it.evadid.sql.DatabaseConfig.DatabaseConfigPartialInformation
import it.evadid.sql.DatabaseHistoryFormat
import upickle.default.*

sealed trait SyncDestinationDefinition derives ReadWriter {

  def isRemote: Boolean = this match {
    case _: SyncDestinationDefinition.SyncDestinationBasedOnConfigMap => ???
    case _: SyncDestinationDefinition.SyncDestinationBasedOnHistoryDatabase => true
    case _: SyncDestinationDefinition.LocalIndexedDbSyncDefinition => false
    case _: SyncDestinationDefinition.LocalStorageSyncDefinition => false
  }

}

object SyncDestinationDefinition {

  case class SyncDestinationBasedOnConfigMap(instanceTypeToCreate: String, config: Map[String, String]) extends SyncDestinationDefinition derives ReadWriter {

  }

  case class SyncDestinationBasedOnHistoryDatabase(databaseConfig: DatabaseConfigPartialInformation, format: DatabaseHistoryFormat) extends SyncDestinationDefinition derives ReadWriter {

    override def toString: String =
      s"DatabaseDestination(${databaseConfig.hostOp.getOrElse("defaultRemoteHost")}->${databaseConfig.databaseOp.getOrElse("defaultRemoteDb")}/${format.useTableForStorage})"
  }

  case class LocalIndexedDbSyncDefinition(dbName: String, storeName: String, dbVersion: Int) extends SyncDestinationDefinition derives ReadWriter {
    override def toString: String = s"DatabaseDestination(browser/IndexedDb->${dbName}/${storeName})"
  }

  case class LocalStorageSyncDefinition(maxValueCharakterSize: Int) extends SyncDestinationDefinition derives ReadWriter {
    override def toString: String = "DatabaseDestination(browser/localStorage->default)"
  }

}

