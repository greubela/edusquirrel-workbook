package it.evadid.workbook.model.interaction.sync

case class UsageContext(programId: String, scenarioId: String, userId: String) {

  def toSyncContext(key: String): SyncContext = SyncContext(programId, scenarioId, userId, key)

}
