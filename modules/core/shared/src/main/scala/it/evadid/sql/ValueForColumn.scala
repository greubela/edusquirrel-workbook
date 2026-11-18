package it.evadid.sql

import upickle.default.*

case class ValueForColumn(value: String, column: DatabaseColumn) derives ReadWriter {



}
