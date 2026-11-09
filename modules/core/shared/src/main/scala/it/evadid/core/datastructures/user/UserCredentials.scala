package it.evadid.core.datastructures.user

import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.llm.LlmConfiguration
import it.evadid.sql.DatabaseConfiguration
import upickle.default.*

/** Transient session state. Never include this object in account preferences or saved sessions. */
case class UserCredentials(signedBackendToken: Option[SignedToken] = None,
    singleAccessToken: Option[SingleAccessToken] = None,
    llmApiConfig: Option[LlmConfiguration] = None,
    databaseConfig: Option[DatabaseConfiguration] = None) derives ReadWriter {
  override def toString: String = "UserCredentials(redacted)"
}

case class SavedUserPreferences(user: User, config: UserConfig) derives ReadWriter
