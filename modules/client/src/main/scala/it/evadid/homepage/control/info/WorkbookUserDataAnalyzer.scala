package it.evadid.homepage.control.info

import it.evadid.core.datastructures.user.*
import it.evadid.core.util.InfoUtil
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.homepage.control.info.WorkbookUserDataAnalyzer.*
import it.evadid.homepage.control.model.*
import it.evadid.homepage.control.singletons.HomepageDefaults
import it.evadid.util.DownloadToDisc
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.WorkbookInteractionUntyped
import it.evadid.workbook.elements.structureElements.Workbook
import it.evadid.workbook.interaction.sync.SyncContext
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import upickle.default.*

import java.time.LocalDateTime

object WorkbookUserDataAnalyzer {


  case class SessionData(
                          timestampStorage: LocalDateTime,
                          knownUsageData: Map[SyncContext, String],
                          userInfo: User,
                          workbookInfo: AllWorkbookInfo) derives ReadWriter

}

case class WorkbookUserDataAnalyzer(logger: Logger, downloadToDisc: DownloadToDisc, homepageInfo: HomepageInfo) {


  def createSessionData(): Option[SessionData] = {

    if (homepageInfo.workbookInfo.isEmpty || homepageInfo.userInfo.isEmpty) {
      None
    } else {
      val allInteractions: List[WorkbookInteractionUntyped] = homepageInfo.workbookInfo.map(_.loadedWorkbook.allContainedInteractionsById.values.toList).getOrElse(List())
      val userInfo = homepageInfo.userInfo.get.removeToken()
      val workbookInfo = homepageInfo.workbookInfo.get
      val historyMap: Map[SyncContext, String] = allInteractions.map(interaction => {
        val syncContext = homepageInfo.toContext.toSyncContext(interaction.interactionVariable.keyForSerialization)
        val historySerialized = interaction.interactionVariable.serializerHistory.serialize(interaction.interactionVariable.currentHistory)
        syncContext -> historySerialized
      }).toMap

      val data = SessionData(LocalDateTime.now(), historyMap, userInfo.user, workbookInfo)
      Some(data)
    }


  }


  def downloadAllData(): Unit = {
    logger.logInfo("WorkbookUserDataAnalyzer: now downloading all session data!")
    val dataOp = createSessionData()
    if (dataOp.nonEmpty) {
      val data = dataOp.get
      val name = s"${InfoUtil.datetimeFormattedForFilenames(data.timestampStorage)}-eva-${data.userInfo.id}-${data.workbookInfo.loadedWorkbook.elementId.replace("[/]", "-")}.eva"

      downloadToDisc.downloadFile(name, write(data))

    }
  }


  /*
    private def upload(file: FileDescription): Unit = {
      logger.logInfo(s"WorkbookUserDataAnalyzer: Trying to load prior session data based on file ${file.asUrlString}!")
      file.loadData().foreach(loadedFile => {
        val str = loadedFile.fileDataAsUtf8String
        val data: SessionData = upickle.default.read(str)

        tryToLoad(data)
      })(using ExecutionContext.global)
    }
  */

}
