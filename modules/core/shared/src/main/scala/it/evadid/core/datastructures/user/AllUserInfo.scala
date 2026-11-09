package it.evadid.core.datastructures.user

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import upickle.default.*
case class AllUserInfo(user: User, credentials: UserCredentials, config: UserConfig) derives ReadWriter{

  override def toString: String = {
    s"${user.name} (${user.mail}@${user.id}, logged in: ${credentials.signedBackendToken.nonEmpty}, syncDestinations: ${config.syncDestinations.size})"
  }

  def effectiveSyncDestinations: List[it.evadid.workbook.interaction.sync.SyncInformation] = {
    import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.SyncDestinationBasedOnHistoryDatabase
    config.syncDestinations.map { info => info.syncDestination match {
      case destination: SyncDestinationBasedOnHistoryDatabase if destination.databaseConfig == it.evadid.sql.DatabaseConfiguration.defaultDb =>
        credentials.databaseConfig.fold(info)(database => info.copy(syncDestination = destination.copy(databaseConfig = database.publicInformation)))
      case _ => info
    }}
  }

  def withoutCredentials(): AllUserInfo = {
    copy(credentials = UserCredentials())
  }

}

object AllUserInfo {
  def createNewUser(name: String, mail: String, config: UserConfig): AllUserInfo = {
    val user = User.createNewUser(name, mail)
    AllUserInfo(user, UserCredentials(signedBackendToken = None), config)
  }

}
