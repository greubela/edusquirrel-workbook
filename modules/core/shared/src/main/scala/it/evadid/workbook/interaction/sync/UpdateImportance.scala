package it.evadid.workbook.interaction.sync

import upickle.default.*

enum UpdateImportance derives ReadWriter{
  case DEFAULT // default value for new variables
  case TEMPORARY // keep until the next real event comes
  case MINOR // keep until the next major event comes
  case MAJOR // always keep in history
}
