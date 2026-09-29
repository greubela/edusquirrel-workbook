package it.evadid.homepage.control.info

import it.evadid.core.datastructures.user.*
import it.evadid.core.util.io.Serializer
import it.evadid.homepage.control.info.WorkbookUserDataAnalyzer.*
import it.evadid.homepage.control.model.*
import it.evadid.homepage.control.singletons.HomepageDefaults
import it.evadid.util.DownloadToDisc
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.structureElements.Workbook
import it.evadid.workbook.elements.structureElements.Workbook.WorkbookMetadata
import it.evadid.workbook.interaction.variable.InteractionVariableHistorySerialized
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import upickle.default.ReadWriter.join

object WorkbookUserDataAnalyzer {

  private given userConfigRW: upickle.ReadWriter[UserConfig] = HomepageDefaults.defaultSerializerUserConfig.uPickleReadWrite

  private given usiRW: upickle.ReadWriter[AllUserInfo] = upickle.macroRW

  private given woRW: upickle.ReadWriter[Workbook] = WorkbookElementFactory.serializerRegularJsonWorkbook.uPickleReadWrite

  private given seRW: upickle.ReadWriter[SessionData] = upickle.macroRW

  val serializerSessionData: Serializer[SessionData] = Serializer.fromUpickleJson(seRW)

  case class SessionData(currentUserInfo: AllUserInfo, interactionHistory: Map[String, InteractionVariableHistorySerialized], workbook: Workbook, epochTimestampMillis: Long)


}

case class WorkbookUserDataAnalyzer(logger: Logger, downloadToDisc: DownloadToDisc, userInfo: AllUserInfo, workbookInfo: AllWorkbookInfo) {

  def downloadAllData(): Unit = {
    logger.logInfo("WorkbookUserDataAnalyzer: now downloading all session data!")
    val allInteractions: List[WorkbookInteractionElement[?]] = workbookInfo.loadedWorkbook.allContainedInteractions
    val history: Map[String, InteractionVariableHistorySerialized] = allInteractions.map(interaction => interaction.interactionVariable.keyForSerialization -> interaction.interactionVariable.serializedHistory).toMap
    val data = SessionData(userInfo, history, workbookInfo.loadedWorkbook, System.currentTimeMillis())
    val str = upickle.default.write(data)
    val name = s"${data.currentUserInfo}-${data.workbook.workbookId}-${data.epochTimestampMillis}.json"
    downloadToDisc.downloadFile(name, str)
  }

  private def tryToLoad(sessionData: SessionData): Unit = {
    logger.logInfo("WorkbookUserDataAnalyzer: now trying to load prio session data!")
    if (sessionData.currentUserInfo.user.id == userInfo.user.id) {
      workbookInfo.loadedWorkbook.allContainedInteractions.foreach(curInteraction => {
        sessionData.interactionHistory.foreach(historyTup => if (historyTup._1 == curInteraction.interactionVariable.keyForSerialization) {
          curInteraction.interactionVariable.updateHistory(_.withAddedEvents(historyTup._2, curInteraction.serializerInteractionContent))
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
