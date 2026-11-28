package it.evadid.workbook.sync

import upickle.default.*

enum UpdateImportance derives ReadWriter{
  @upickle.implicits.key("DEFAULT")
  case DEFAULT // default value for new variables
  @upickle.implicits.key("TEMPORARY")
  case TEMPORARY // keep until the next real event comes
  @upickle.implicits.key("MINOR")
  case MINOR // keep until the next major event comes
  @upickle.implicits.key("MAJOR")
  case MAJOR // always keep in history
}
