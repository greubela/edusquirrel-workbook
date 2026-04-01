package workbook.model.info

import datastructures.core.language.HumanLanguage
import workbook.model.WorkbookSection
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.interaction.sync.{LocalStorageSync, SyncInformation, SyncStrategy}
import workbook.user.User


case class WorkbookConfig(currentWorkbookLanguage: HumanLanguage, activeSection: Option[WorkbookSection], currentUser: User) {
  
  def getSyncDestinations(): List[SyncInformation] = 
    List(SyncInformation(LocalStorageSync, SyncStrategy.SYNC_EVERYTHING))
  

}
