package it.evadid.sql

import it.evadid.sql.DatabaseConfig.DatabaseConfigPartialInformation
import upickle.default.*

case class DatabaseTable(name: String, inDatabase: DatabaseConfigPartialInformation = DatabaseConfigPartialInformation(),
                         hasColumns: Seq[DatabaseTableColumn] = Seq.empty) derives ReadWriter {
  require(name.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"), "Invalid database table identifier")
}

object DatabaseTable {
  val commandLog: DatabaseTable = DatabaseTable("commandLog")
  val syncedLog: DatabaseTable = DatabaseTable("syncedLog")
}
