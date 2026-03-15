package workbook.model.info

import contentmanagement.model.language.HumanLanguage
import workbook.model.WorkbookSection
import workbook.model.interaction.sync.{LocalStorageSync, SyncInformation, SyncStrategy}
import workbook.user.User
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement


case class WorkbookConfig(currentWorkbookLanguage: HumanLanguage, activeSection: Option[WorkbookSection], currentUser: User) {
  
  def getSyncDestinations(): List[SyncInformation] = 
    List(SyncInformation(LocalStorageSync, SyncStrategy.SYNC_EVERYTHING))
  

}
