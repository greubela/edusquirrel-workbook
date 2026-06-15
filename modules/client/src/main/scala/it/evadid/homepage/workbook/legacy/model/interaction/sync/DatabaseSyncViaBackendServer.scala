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

    val userId: String = info.current.userInfo.map(_.user.id).getOrElse("unknownUser")
    val scenarioId: String = info.current.workbookInfo.map(_.loadedWorkbook.workbookId).getOrElse("unknownScenario")
    val time = datetimeFormattedForLog()

    val eventdata: String =
      s"""{
      "type": "syncEvent",
      "name": "syncInfo",
      "source": "DatabaseSync",
      "key": "$key",
      "data": "$value"
    }"""

    val request = SQLCommands.SyncToDbRequest("edusquirrel", scenarioId, userId, time, key, eventdata)
    SQLCommands.syncToDbCommand.sendCommandTo(backend, Logger(), request)
  }
  override def syncAllFrom(): Map[String, String] = Map()

  override def syncKeyFrom(key: String): Option[String] = None

  // todo
}
