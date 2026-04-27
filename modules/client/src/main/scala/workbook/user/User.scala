package workbook.user

import workbook.model.interaction.sync.{LocalStorageSync, SyncInformation, SyncStrategy}



case class User(name: String, mail: String) {

 def id: String = mail

}


