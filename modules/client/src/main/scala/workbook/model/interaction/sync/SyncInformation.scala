package workbook.model.interaction.sync

import workbook.model.interaction.InteractionVariable.*

case class SyncInformation(syncSource: SyncDestination, syncStrategy: SyncStrategy) {
  
}


object SyncInformation {
  
  val syncEverythingToBrowser: SyncInformation = SyncInformation(LocalStorageSync, SyncStrategy.SYNC_EVERYTHING)
  
  //val syncMajorToDb: SyncInformation = 
  
}