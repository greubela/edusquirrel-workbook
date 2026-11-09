package it.evadid.core.datastructures.user

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import upickle.default.*
case class AllUserInfo(user: User, credentials: UserCredentials, config: UserConfig) derives ReadWriter{

  override def toString: String = {
    s"${user.name} (${user.mail}@${user.id}, logged in: ${credentials.signedBackendToken.nonEmpty}, syncDestinations: ${config.syncDestinations.size})"
  }

  def withoutCredentials(): AllUserInfo = {
    copy(credentials = UserCredentials(), config = config.withoutCredentials)
  }

}

object AllUserInfo {
  def createNewUser(name: String, mail: String, config: UserConfig): AllUserInfo = {
    val user = User.createNewUser(name, mail)
    AllUserInfo(user, UserCredentials(signedBackendToken = None), config)
  }

}
