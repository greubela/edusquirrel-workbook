package it.evadid.sql

case class DatabaseTable(name: String, inDatabase: Option[DatabaseConfiguration] = None,
                         columns: Seq[DatabaseColumn] = Seq.empty) {
  require(name.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"), "Invalid database table identifier")
}

object DatabaseTable {
  val commandLog: DatabaseTable = DatabaseTable("commandLog")
  val syncedLog: DatabaseTable = DatabaseTable("syncedLog")
}
