package it.evadid.sql

import it.evadid.sql.DatabaseConfig.DatabaseConfigPartialInformation
import upickle.default.*

case class DatabaseTable(inDatabase: DatabaseConfigPartialInformation, hasColumns: Seq[DatabaseTableColumn]) derives ReadWriter {


}
