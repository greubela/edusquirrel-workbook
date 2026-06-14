package it.evadid.homepage.workbook.legacy.model.interaction.sync

import it.evadid.core.util.InfoUtil.datetimeFormattedForLog
import it.evadid.distribution.commandTypes.SQLCommands
import it.evadid.homepage.control.HtmlFullWorkbookApp
import it.evadid.util.Logger
import it.evadid.workbook.model.interaction.sync.SyncDestination

object DatabaseSyncViaBackendServer extends SyncDestination{

  def syncTo(key: String, value: String): Unit = {
    val info = HtmlFullWorkbookApp.fullInfo
    val backend = info.technical.backendServerExecutor

    val userId = info.current.userInfo.map(_.user.id).getOrElse("unknownUser")
    val scenarioId = info.current.workbookInfo.map(_.getMetadata().workbookId)
    val time = datetimeFormattedForLog()

    val request = SQLCommands.SyncToDbRequest("edusquirrel", userId, key, time, value)
    SQLCommands.syncToDbCommand.sendCommandTo(backend, Logger(), request)
  }
  override def syncAllFrom(): Map[String, String] = Map()

  override def syncKeyFrom(key: String): Option[String] = None

  // todo
}
