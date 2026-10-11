package it.evadid.homepage.control.info

import it.evadid.core.datastructures.user.*
import it.evadid.core.util.io.Serializer
import it.evadid.homepage.control.info.WorkbookUserDataAnalyzer.*
import it.evadid.homepage.control.model.*
import it.evadid.homepage.control.singletons.HomepageDefaults
import it.evadid.util.DownloadToDisc
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement, WorkbookInteractionUntyped}
import it.evadid.workbook.elements.structureElements.Workbook
import it.evadid.workbook.elements.structureElements.Workbook.WorkbookMetadata
import it.evadid.workbook.interaction.variable.InteractionVariableHistory.InteractionVariableHistoryUntyped
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import upickle.default.ReadWriter.join

object WorkbookUserDataAnalyzer {

  private given userConfigRW: upickle.ReadWriter[UserConfig] = HomepageDefaults.defaultSerializerUserConfig.uPickleReadWrite

  private given usiRW: upickle.ReadWriter[AllUserInfo] = upickle.macroRW

  private given woRW: upickle.ReadWriter[Workbook] = WorkbookElementFactory.serializerRegularJsonWorkbook.uPickleReadWrite

  given seRW: upickle.ReadWriter[SessionData] = upickle.macroRW

  val serializerSessionData: Serializer[SessionData] = Serializer.fromUpickleJson(seRW)

  case class SessionData(currentUserInfo: AllUserInfo, interactionHistorySerialized: Map[String, String], workbook: Workbook, epochTimestampMillis: Long)


}

case class WorkbookUserDataAnalyzer(logger: Logger, downloadToDisc: DownloadToDisc, userInfo: AllUserInfo, workbookInfo: AllWorkbookInfo) {

  def downloadAllData(): Unit = {
    logger.logInfo("WorkbookUserDataAnalyzer: now downloading all session data!")
    val allInteractions: List[WorkbookInteractionUntyped] = workbookInfo.loadedWorkbook.allInteractionsInThisInstance
    val history: Map[String, String] = allInteractions.map(interaction => interaction.interactionVariable.keyForSerialization -> interaction.interactionVariable.serializeHistory(interaction.interactionVariable.history)).toMap
    val data = SessionData(userInfo, history, workbookInfo.loadedWorkbook, System.currentTimeMillis())
    val str = serializerSessionData.serialize(data)
    val name = s"${data.currentUserInfo}-${data.workbook.workbookId}-${data.epochTimestampMillis}.json"
    downloadToDisc.downloadFile(name, str)
  }

  private def tryToLoad(sessionData: SessionData): Unit = {
    logger.logInfo("WorkbookUserDataAnalyzer: now trying to load prio session data!")
    if (sessionData.currentUserInfo.user.id == userInfo.user.id) {
      workbookInfo.loadedWorkbook.allInteractionsInThisInstance.foreach(curInteraction => {
        sessionData.interactionHistorySerialized.foreach(historyTup => if (historyTup._1 == curInteraction.interactionVariable.keyForSerialization) {
          curInteraction.interactionVariable.withAddedEvents(logger, historyTup._2)
        })
      })
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
