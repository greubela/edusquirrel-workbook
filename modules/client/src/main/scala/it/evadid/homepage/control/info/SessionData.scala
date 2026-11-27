package it.evadid.homepage.control.info

import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig}
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.homepage.control.model.{AllWorkbookInfo, FullInfo, HomepageInfo}
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.interaction.sync.SyncContext
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import upickle.default.*

import java.time.LocalDateTime
import it.evadid.homepage.control.model.FullInfo.*
case class SessionData(
                        timestampStorage: LocalDateTime,
                        knownUsageData: Map[SyncContext, InteractionHistoryJson],
                        userInfo: User,
                        userConfig: Option[UserConfig],
                        workbookInfo: AllWorkbookInfo) derives ReadWriter {
  /** The workbook to continue: the open one when it is this session's workbook, else the copy stored in the file. */
  def workbookToContinue(open: Option[AllWorkbookInfo]): AllWorkbookInfo =
    open.filter(_.loadedWorkbook.workbookId == workbookInfo.loadedWorkbook.workbookId).getOrElse(workbookInfo)

  /** Download name; workbook ids may contain path separators. */
  def fileName: String =
    it.evadid.core.util.InfoUtil.downloadName(s"eva ${userInfo.id} ${workbookInfo.loadedWorkbook.elementId}", timestampStorage) + ".eva"
}

object SessionData {


  def createSessionData(homepageInfo: HomepageInfo): Option[SessionData] = {
    if (homepageInfo.workbookInfo.isEmpty || homepageInfo.userInfo.isEmpty) {
      None
    } else {
      val allInteractions: Seq[WorkbookInteractionElement[? <: InteractionFormat]] = homepageInfo.workbookInfo.map(_.loadedWorkbook.allContainedInteractionsById.values.toList).getOrElse(List())
      val userInfo = homepageInfo.userInfo.get.withoutCredentials()
      val workbookInfo = homepageInfo.workbookInfo.get
      
      // Create InteractionHistoryJson instances for each interaction
      val historyMap: Map[SyncContext, InteractionHistoryJson] = allInteractions.map(interaction => {
        val syncContext = homepageInfo.toContext.toSyncContext(interaction.interactionVariable.keyForSerialization)
        val historyJson = InteractionHistoryJson.fromHistory(
          interaction.interactionFormat,
          interaction.interactionVariable.currentHistory
        )
        syncContext -> historyJson
      }).toMap

      val data = SessionData(LocalDateTime.now(), historyMap, userInfo.user, Some(userInfo.config), workbookInfo)
      Some(data)
    }
  }

}
