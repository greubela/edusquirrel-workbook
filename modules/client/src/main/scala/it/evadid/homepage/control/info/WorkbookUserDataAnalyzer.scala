package it.evadid.homepage.control.info

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig}
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.homepage.control.info.WorkbookUserDataAnalyzer.SessionData
import it.evadid.homepage.control.model.*
import it.evadid.homepage.control.model.AllWorkbookInfo.*
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.util.DownloadToDisc
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.interaction.variable.{InteractionVariableHistorySerialized, InteractionVariableStateSerialized}
import upickle.default.ReadWriter.join

import java.time.LocalDateTime

object WorkbookUserDataAnalyzer {

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

  private given ustRW: upickle.ReadWriter[SignedToken] = DefaultSerializer.serializerSignedUserTokenInfo.uPickleReadWrite

  private given usRW: upickle.ReadWriter[User] = upickle.macroRW

  private given userConfigRW: upickle.ReadWriter[UserConfig] = fullInfo.defaults.defaultSerializerUserConfig.uPickleReadWrite

  private given usiRW: upickle.ReadWriter[AllUserInfo] = upickle.macroRW

  private given cidRW: upickle.ReadWriter[LanguageMapContentId] = upickle.macroRW

  private given meRW: upickle.ReadWriter[WorkbookMetadata] = upickle.macroRW

  private given seRW: upickle.ReadWriter[SessionData] = upickle.macroRW

  val serializerSessionData: Serializer[SessionData] = Serializer.fromUpickleJson(seRW)

  case class SessionData(currentUserInfo: AllUserInfo, interactionHistory: Map[String, InteractionVariableHistorySerialized], metadata: WorkbookMetadata, epochTimestampMillis: Long)


}

case class WorkbookUserDataAnalyzer(logger: Logger, downloadToDisc: DownloadToDisc, userInfo: AllUserInfo, workbookInfo: AllWorkbookInfo) {

  def downloadAllData(): Unit = {
    logger.logInfo("WorkbookUserDataAnalyzer: now downloading all session data!")
    val allInteractions: List[WorkbookInteractionElement[?]] = workbookInfo.loadedWorkbook.allContainedInteractions
    val history: Map[String, InteractionVariableHistorySerialized] = allInteractions.map(interaction => interaction.interactionVariable.keyForSerialization -> interaction.interactionVariable.serializedHistory).toMap
    val data = SessionData(userInfo, history, workbookInfo.getMetadata(), System.currentTimeMillis())
    val str = upickle.default.write(data)
    val name = s"${data.currentUserInfo}-${data.metadata.workbookId}-${data.epochTimestampMillis}.json"
    downloadToDisc.downloadFile(name, str)
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

