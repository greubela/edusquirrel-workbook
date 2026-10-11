package it.evadid.workbook.interaction.sync.model
import upickle.default.*
enum SyncDirection derives ReadWriter {
  case CLIENT_TO_SERVER
  case SERVER_TO_CLIENT
}