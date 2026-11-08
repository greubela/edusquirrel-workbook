package it.evadid.homepage.control.info

import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig}
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.homepage.control.model.{AllWorkbookInfo, HomepageInfo}
import it.evadid.workbook.abstractions.WorkbookInteractionUntyped
import it.evadid.workbook.interaction.sync.SyncContext
import upickle.default.*

import java.time.LocalDateTime

case class SessionData(
                        timestampStorage: LocalDateTime,
                        knownUsageData: Map[SyncContext, String],
                        userInfo: User,
                        userConfig: Option[UserConfig],
                        workbookInfo: AllWorkbookInfo) derives ReadWriter

object SessionData {


  def createSessionData(homepageInfo: HomepageInfo): Option[SessionData] = {
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

      val data = SessionData(LocalDateTime.now(), historyMap, userInfo.user, Some(userInfo.config), workbookInfo)
      Some(data)
    }
  }

}
