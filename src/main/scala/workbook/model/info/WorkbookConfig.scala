package workbook.model.info

import contentmanagement.model.language.HumanLanguage
import workbook.model.interaction.sync.{LocalStorageSync, SyncInformation, SyncStrategy}
import workbook.user.User


case class WorkbookConfig(currentWorkbookLanguage: HumanLanguage, currentUser: User) {
  
  def getSyncDestinations(): List[SyncInformation] = 
    List(SyncInformation(LocalStorageSync, SyncStrategy.SYNC_EVERYTHING))
  

}
