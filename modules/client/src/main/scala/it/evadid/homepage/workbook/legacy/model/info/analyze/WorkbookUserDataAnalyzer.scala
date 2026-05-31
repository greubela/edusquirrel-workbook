package it.evadid.homepage.workbook.legacy.model.info.analyze

import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.homepage.util.web.DownloadHelper
import it.evadid.homepage.workbook.legacy.model.info.AllWorkbookInfo.WorkbookMetadata
import it.evadid.homepage.workbook.legacy.model.info.control.TechnicalControl
import it.evadid.homepage.workbook.legacy.model.info.{AllUserInfo, AllWorkbookInfo}
import it.evadid.homepage.workbook.legacy.user.User
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import it.evadid.workbook.model.interaction.variable.{SerializedExerciseVariableState, SerializedInteractionHistory}
import upickle.default.ReadWriter.join

import scala.concurrent.ExecutionContext
case class WorkbookUserDataAnalyzer(technical: TechnicalControl, userInfo: AllUserInfo, workbookInfo: AllWorkbookInfo) {

  private given uiRW: upickle.ReadWriter[UpdateImportance] = upickle.readwriter[String].bimap[UpdateImportance](_.toString, UpdateImportance.valueOf)

  private given laRW: upickle.ReadWriter[HumanLanguage] = upickle.readwriter[String].bimap[HumanLanguage](_.toString, str => AppLanguage.humanLanguages.find(_.toString == str).get)

  private given esRW: upickle.ReadWriter[SerializedExerciseVariableState] = upickle.macroRW

  private given li1RW: upickle.ReadWriter[List[SerializedExerciseVariableState]] =
    upickle.readwriter[Seq[SerializedExerciseVariableState]].bimap[List[SerializedExerciseVariableState]](identity, _.toList)

  private given li3RW: upickle.ReadWriter[List[HumanLanguage]] =
    upickle.readwriter[Seq[HumanLanguage]].bimap[List[HumanLanguage]](identity, _.toList)

  private given hiRW: upickle.ReadWriter[SerializedInteractionHistory] = upickle.macroRW

  private given li2RW: upickle.ReadWriter[List[SerializedInteractionHistory]] =
    upickle.readwriter[Seq[SerializedInteractionHistory]].bimap[List[SerializedInteractionHistory]](identity, _.toList)

  private given usRW: upickle.ReadWriter[User] = upickle.macroRW

  private given cidRW: upickle.ReadWriter[LanguageMapContentId] = upickle.macroRW
  
  private given meRW: upickle.ReadWriter[WorkbookMetadata] = upickle.macroRW

  private given seRW: upickle.ReadWriter[SessionData] = upickle.macroRW

  private case class SessionData(currentUserInfo: User, interactionHistory: List[SerializedInteractionHistory], metadata: WorkbookMetadata, epochTimestampMillis: Long)

  def downloadAllData(): Unit = {
    println("download session data :)")
    val history: List[SerializedInteractionHistory] = workbookInfo.loadedWorkbook.allContainedInteractions.map(_.interactionVariable.serializeHistory())
    val data = SessionData(userInfo.user, history, workbookInfo.getMetadata(), System.currentTimeMillis())
    val str = upickle.default.write(data)
    val name = s"${data.currentUserInfo.id}-${data.metadata.workbookId}-${data.epochTimestampMillis}.json"
    DownloadHelper.downloadFile(name, str)
  }

  private def tryToLoad(sessionData: SessionData): Unit = {
    println("Trying to load session data :)")
    if (sessionData.currentUserInfo.id == userInfo.user.id) {
      workbookInfo.loadedWorkbook.allContainedInteractions.foreach(curInteraction => {
        sessionData.interactionHistory.foreach(curHistory => {
          curInteraction.interactionVariable.addHistory(curHistory)
        })
      })
    }
  }

  def upload(file: FileDescription): Unit = {
    println("Trying to parse session data :)")
    technical.fileStore.loadAsFuture(file)(using ExecutionContext.global).foreach(loadedFile => {
      val str = loadedFile.fileDataAsUtf8String
      val data: SessionData = upickle.default.read(str)
      tryToLoad(data)
    })(using ExecutionContext.global)
  }


}

object WorkbookUserDataAnalyzer {


}
