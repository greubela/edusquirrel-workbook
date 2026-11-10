package it.evadid.core.datastructures.user

import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.llm.LlmConfiguration
import it.evadid.sql.DatabaseConfigWithCredentials
import upickle.default.*

/** Client credentials cached in IndexedDB. Never include them in server account preferences or exported sessions. */
case class UserCredentials(signedBackendToken: Option[SignedToken] = None,
    singleAccessToken: Option[SingleAccessToken] = None,
    llmApiConfig: Option[LlmConfiguration] = None,
    databaseConfig: Option[DatabaseConfigWithCredentials] = None) derives ReadWriter {
  override def toString: String = "UserCredentials(redacted)"
}

case class SavedUserPreferences(user: User, config: UserConfig) derives ReadWriter
