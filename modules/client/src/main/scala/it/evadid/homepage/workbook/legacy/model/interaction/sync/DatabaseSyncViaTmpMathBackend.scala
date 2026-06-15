package it.evadid.homepage.workbook.legacy.model.interaction.sync

import it.evadid.core.util.InfoUtil.datetimeFormattedForLog
import it.evadid.distribution.commandTypes.SQLCommands
import it.evadid.homepage.control.HtmlFullWorkbookApp
import it.evadid.homepage.util.web.DownloadHelper
import it.evadid.util.Logger
import it.evadid.workbook.model.interaction.sync.SyncDestination

import scala.concurrent.ExecutionContext

object DatabaseSyncViaTmpMathBackend extends SyncDestination {

  def syncTo(key: String, value: String): Unit = {
    val info = HtmlFullWorkbookApp.fullInfo
    val backend = info.technical.backendServerExecutor

    val userId = info.current.userInfo.map(_.user.id).getOrElse("unknownUser")
    val scenarioId = info.current.workbookInfo.map(_.getMetadata().workbookId)
    val time = datetimeFormattedForLog()

    val request: String =
      s"""{
    "programid": "edusquirrel-workbook",
    "scenarioid": "$scenarioId",
    "userid": "$userId",
    "event": {
      "type": "syncEvent",
      "name": "syncInfo",
      "source": "DatabaseSyncViaTmpMathBackend",
      "data": "$value"
    }
  }"""

    val res = DownloadHelper.postTo("https://api.mismatcheta.org/insert.php", request)
    res.onComplete(res => println("post result: " + res))(using ExecutionContext.global)
  }

  override def syncAllFrom(): Map[String, String] = Map()

  override def syncKeyFrom(key: String): Option[String] = None

}