package it.evadid.sql

import upickle.default.*

case class DatabaseTableColumn(name: String, dataType: String) derives ReadWriter { // todo: mysql dataTypes

}
