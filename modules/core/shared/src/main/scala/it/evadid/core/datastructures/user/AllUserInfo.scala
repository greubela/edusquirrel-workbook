package it.evadid.core.datastructures.user

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.workbook.interaction.sync.SyncInformation

case class AllUserInfo(user: User, token: Option[SignedToken], config: UserConfig) {

  override def toString: String = {
    s"${user.name} (${user.mail}@${user.id}, logged in: ${token.nonEmpty}, syncDestinations: ${config.syncDestinations.size})"
  }
}

object AllUserInfo {
  def createNewUser(name: String, mail: String, config: UserConfig): AllUserInfo = {
    val user = User.createNewUser(name, mail)
    AllUserInfo(user, None, config)
  }


}
