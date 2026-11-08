package it.evadid.core.datastructures.user

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import upickle.default.*
case class AllUserInfo(user: User, token: Option[SignedToken], config: UserConfig) derives ReadWriter{

  override def toString: String = {
    s"${user.name} (${user.mail}@${user.id}, logged in: ${token.nonEmpty}, syncDestinations: ${config.syncDestinations.size})"
  }

  def removeToken(): AllUserInfo = {
    AllUserInfo(user, None, config)
  }

}

object AllUserInfo {
  def createNewUser(name: String, mail: String, config: UserConfig): AllUserInfo = {
    val user = User.createNewUser(name, mail)
    AllUserInfo(user, None, config)
  }

}
