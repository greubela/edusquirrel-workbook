package it.evadid.core.datastructures.user

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.workbook.interaction.sync.SyncInformation

case class AllUserInfo(user: User, token: Option[SignedToken], config: UserConfig) {

}

object AllUserInfo {
  def createNewUser(name: String, mail: String, syncLocations: List[SyncInformation]): AllUserInfo = {
    val user = User.createNewUser(name, mail)
    val config = UserConfig(syncLocations)
    AllUserInfo(user, None, config)
  }

}
