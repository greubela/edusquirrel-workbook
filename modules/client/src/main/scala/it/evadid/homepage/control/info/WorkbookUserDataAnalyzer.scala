package it.evadid.homepage.control.info

import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.user.User
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.homepage.control.change.HomepageContentControl
import it.evadid.homepage.control.model.*
import it.evadid.homepage.control.model.AllWorkbookInfo.*
import it.evadid.util.DownloadToDisc
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.interaction.variable.{InteractionVariableHistorySerialized, InteractionVariableStateSerialized}
import upickle.default.ReadWriter.join

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext

case class WorkbookUserDataAnalyzer(logger: Logger, downloadToDisc: DownloadToDisc, userInfo: AllUserInfo, workbookInfo: AllWorkbookInfo) {

  private given ldt: upickle.ReadWriter[LocalDateTime] = DefaultSerializer.serializerLocalDateTimeString.uPickleReadWrite

  private given uiRW: upickle.ReadWriter[UpdateImportance] = upickle.readwriter[String].bimap[UpdateImportance](_.toString, UpdateImportance.valueOf)

  private given laRW: upickle.ReadWriter[HumanLanguage] = upickle.readwriter[String].bimap[HumanLanguage](_.toString, str => AppLanguage.humanLanguages.find(_.toString == str).get)

  private given esRW: upickle.ReadWriter[InteractionVariableStateSerialized] = upickle.macroRW

  private given li1RW: upickle.ReadWriter[List[InteractionVariableStateSerialized]] =
    upickle.readwriter[Seq[InteractionVariableStateSerialized]].bimap[List[InteractionVariableStateSerialized]](identity, _.toList)

  private given li3RW: upickle.ReadWriter[List[HumanLanguage]] =
    upickle.readwriter[Seq[HumanLanguage]].bimap[List[HumanLanguage]](identity, _.toList)

  private given hiRW: upickle.ReadWriter[InteractionVariableHistorySerialized] = upickle.macroRW

  private given li2RW: upickle.ReadWriter[List[InteractionVariableStateSerialized]] =
    upickle.readwriter[Seq[InteractionVariableStateSerialized]].bimap[List[InteractionVariableStateSerialized]](identity, _.toList)

  private given usRW: upickle.ReadWriter[User] = upickle.macroRW

  private given cidRW: upickle.ReadWriter[LanguageMapContentId] = upickle.macroRW

  private given meRW: upickle.ReadWriter[WorkbookMetadata] = upickle.macroRW

  private given seRW: upickle.ReadWriter[SessionData] = upickle.macroRW

  private case class SessionData(currentUserInfo: User, interactionHistory: Map[String, InteractionVariableHistorySerialized], metadata: WorkbookMetadata, epochTimestampMillis: Long)

  def downloadAllData(): Unit = {

    logger.logInfo("WorkbookUserDataAnalyzer: now downloading all session data!")
    val allInteractions: List[WorkbookInteractionElement[?]] = workbookInfo.loadedWorkbook.allContainedInteractions
    val history: Map[String, InteractionVariableHistorySerialized] = allInteractions.map(interaction => interaction.interactionVariable.keyForSerialization -> interaction.interactionVariable.serializedHistory).toMap
    val data = SessionData(userInfo.user, history, workbookInfo.getMetadata(), System.currentTimeMillis())
    val str = upickle.default.write(data)
    val name = s"${data.currentUserInfo.personId}-${data.metadata.workbookId}-${data.epochTimestampMillis}.json"
    downloadToDisc.downloadFile(name, str)
  }

  private def tryToLoad(sessionData: SessionData): Unit = {
    logger.logInfo("WorkbookUserDataAnalyzer: now trying to load prio session data!")
    if (sessionData.currentUserInfo.personId == userInfo.user.personId) {
      workbookInfo.loadedWorkbook.allContainedInteractions.foreach(curInteraction => {
        sessionData.interactionHistory.foreach(historyTup => if (historyTup._1 == curInteraction.interactionVariable.keyForSerialization) {
          curInteraction.interactionVariable.updateHistory(_.withAddedEvents(historyTup._2, curInteraction.serializer))
        })
      })
    }
  }

  def upload(file: FileDescription): Unit = {
    logger.logInfo(s"WorkbookUserDataAnalyzer: Trying to load prior session data based on file ${file.asUrlString}!")
    file.loadData().foreach(loadedFile => {
      val str = loadedFile.fileDataAsUtf8String
      val data: SessionData = upickle.default.read(str)
      tryToLoad(data)
    })(using ExecutionContext.global)
  }


}

