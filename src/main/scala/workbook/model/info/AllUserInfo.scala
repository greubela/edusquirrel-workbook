package workbook.model.info

import workbook.model.interaction.sync.{LocalStorageSync, SyncInformation, SyncStrategy}
import workbook.user.User

case class AllUserInfo(user: User, config: UserConfig){

}

object AllUserInfo {
  

}
