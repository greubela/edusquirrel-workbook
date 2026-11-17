package it.evadid.sql

import upickle.default.*

/** The SQL types used by application schema declarations. */
enum MySqlType derives ReadWriter {
  case BigInt(unsigned: Boolean = false)
  case VarChar(length: Int)
  case DateTime(precision: Int)
  case Timestamp
  case LongText
  case Json
}

case class DatabaseColumn(columnName: String, mySqlType: MySqlType) derives ReadWriter {
  require(columnName.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"), "Invalid database column identifier")
}
