package it.evadid.workbook.interaction.sync

import upickle.default.*

case class UsageContext(programId: String, scenarioId: String, userId: String) derives ReadWriter {

  def toSyncContext(key: String): SyncContext = SyncContext(programId, scenarioId, userId, key)

}
